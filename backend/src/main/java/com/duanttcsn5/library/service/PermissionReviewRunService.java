package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import java.net.URI;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;

/** Local source workflow: fixed Maven target, separate PostgreSQL database, no live business probes. */
@Service
public class PermissionReviewRunService {
    private final PermissionReviewRepository repository;
    private final PermissionReviewService reviews;
    private final PermissionReviewInspector inspector;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final String databaseUrl, username, password;
    private final boolean launchEnabled;
    private final GitHubPermissionReviewGateway github;
    private final boolean githubMode;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"permission-review-runner");t.setDaemon(true);return t;});
    private volatile Process child;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PermissionReviewRunService.class);

    public PermissionReviewRunService(PermissionReviewRepository repository, PermissionReviewService reviews,
            PermissionReviewInspector inspector, UserRepository users, ObjectMapper mapper, Validator validator,
            @Value("${spring.datasource.url}") String url, @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${app.permission-review.launch-enabled:true}") boolean launchEnabled,
            @Value("${app.permission-review.runner:local}") String runner,
            GitHubPermissionReviewGateway github) {
        this.repository=repository;this.reviews=reviews;this.inspector=inspector;this.users=users;
        this.mapper=mapper;this.validator=validator;this.databaseUrl=url;this.username=username;this.password=password;this.launchEnabled=launchEnabled;
        this.github=github;this.githubMode="github".equalsIgnoreCase(runner);
        if(!githubMode && !"local".equalsIgnoreCase(runner))
            throw new IllegalArgumentException("Unknown permission review runner: " + runner);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() { if(launchEnabled && !githubMode)repository.recoverInterruptedJobs(); }

    @PreAuthorize("hasRole('ADMIN')")
    public RunState start(Long actorId) {
        var actor=actorId==null?null:users.findById(actorId).orElse(null);
        if(actor==null || !"ACTIVE".equals(actor.getStatus()) || actor.getRole()==null || !"ADMIN".equals(actor.getRole().getCode()))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ Quản trị hệ thống được chạy kiểm thử.");
        if(launchEnabled) {
            if(githubMode) { github.requireConfigured(); repository.expireStaleJobs(); }
            else backendDirectory();
        }
        UUID key=UUID.randomUUID(); String fingerprint=inspector.inspect().fingerprint();
        try { repository.createJob(key,actorId,actor.getFullName(),fingerprint); }
        catch(DuplicateKeyException exception) { throw new ApiException(HttpStatus.CONFLICT,"PERMISSION_RUN_ACTIVE","Một lần kiểm thử đang chạy. Vui lòng chờ kết quả."); }
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if(launchEnabled) {
            if(githubMode)executor.submit(()->dispatchGitHub(key,fingerprint));
            else executor.submit(()->execute(key,actorId,fingerprint,authentication));
        }
        return get(key);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public RunState get(UUID key) {if(githubMode)repository.expireStaleJobs();return repository.job(key).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PERMISSION_RUN_NOT_FOUND","Không tìm thấy lần kiểm thử."));}
    @PreAuthorize("hasRole('ADMIN')")
    public RunState latest() { if(githubMode)repository.expireStaleJobs();return repository.latestJob().orElse(null); }

    private void dispatchGitHub(UUID key, String fingerprint) {
        try {
            github.dispatch(key, fingerprint);
            repository.running(key);
        } catch(Exception ex) {
            LOG.warn("GitHub permission review dispatch {} failed: {}", key, ex.toString());
            repository.finishJob(key,"FAILED","Không khởi chạy được GitHub Actions. Kiểm tra token, repository và workflow; xem log Backend.",null);
        }
    }

    private void execute(UUID key,Long actor,String fingerprint,Authentication authentication) {
        var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(authentication);SecurityContextHolder.setContext(security);
        boolean databaseCreated=false;
        Long reportId=null; String testDatabase="s4092_review_"+key.toString().replace("-","");
        try {
            Path backend=backendDirectory(); Path directory=backend.resolve("target/permission-review/jobs");Files.createDirectories(directory);
            Path output=directory.resolve(key+".json");Path log=directory.resolve(key+".log");
            prepareDatabase(testDatabase);databaseCreated=true;
            List<String> command=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                    ?List.of("cmd.exe","/d","/c","mvnw.cmd","-B","-Dtest=FullPermissionDatabaseTest","test")
                    :List.of("sh","./mvnw","-B","-Dtest=FullPermissionDatabaseTest","test");
            ProcessBuilder builder=new ProcessBuilder(command).directory(backend.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            Map<String,String> env=builder.environment();
            env.put("S4_09_2_DB_TEST","true");env.put("DB_NAME",testDatabase);
            env.put("DB_HOST","127.0.0.1");env.put("DB_PORT","5433");env.put("DB_USERNAME",username);env.put("DB_PASSWORD",password);
            env.put("PERMISSION_REVIEW_RUN_KEY",key.toString());env.put("PERMISSION_REVIEW_RESULT",output.toString());
            env.put("PERMISSION_REVIEW_LIVE_DB",liveDatabase());
            env.put("BOOTSTRAP_ADMIN_PASSWORD","");env.put("MAIL_USERNAME","");env.put("MAIL_PASSWORD","");env.put("MAIL_HOST","127.0.0.1");
            repository.running(key);child=builder.start();
            boolean done=child.waitFor(15,TimeUnit.MINUTES);
            if(!done){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();throw new IllegalStateException("timeout");}
            int exit=child.exitValue();child=null;
            if(Files.exists(output) && Files.size(output)<=16*1024*1024) {
                ImportReport report=mapper.readValue(Files.readString(output),ImportReport.class);
                if(!validator.validate(report).isEmpty() || !key.equals(report.runKey()) || !fingerprint.equals(report.fingerprint())
                        || report.schemaVersion()!=2 || !"ISOLATED_POSTGRES".equals(report.environment()) || !"REGRESSION".equals(report.kind()))
                    throw new IllegalStateException("invalid worker evidence");
                reportId=reviews.importReport(report,actor).summary().id();
            }
            if(exit!=0 || reportId==null)repository.finishJob(key,"FAILED","Bộ kiểm thử chưa đạt hoặc chưa tạo được biên bản. Xem kết quả đã lưu; log chi tiết nằm trong backend/target/permission-review/jobs.",reportId);
            else repository.finishJob(key,"SUCCEEDED","Đã chạy xong. Mở biên bản để xem độ bao phủ và điều kiện hoàn tất.",reportId);
        } catch(Exception exception) {
            if(exception instanceof InterruptedException)Thread.currentThread().interrupt();
            repository.finishJob(key,"FAILED","Chưa chạy xong kiểm thử. Kiểm tra PostgreSQL 5433, quyền tạo database thử, JDK 17 và Maven Wrapper; xem hướng dẫn S4-09.2.",reportId);
        } finally {
            if(databaseCreated)try { dropDatabase(testDatabase); } catch(Exception cleanupFailure) { repository.cleanupWarning(key); }
            SecurityContextHolder.clearContext();
        }
    }
    private Path backendDirectory() {
        Path current=Path.of("").toAbsolutePath().normalize();
        Path backend=Files.isRegularFile(current.resolve("pom.xml"))?current:current.resolve("backend");
        if(!Files.isRegularFile(backend.resolve("mvnw.cmd")) || !Files.isRegularFile(backend.resolve("src/test/java/com/duanttcsn5/library/FullPermissionDatabaseTest.java")))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PERMISSION_RUN_SOURCE_REQUIRED","Hãy chạy từ source bằng RUN_ALL.cmd; bộ chạy cần Maven Wrapper và file kiểm thử S4-09.2.");
        return backend;
    }
    private String liveDatabase() {return URI.create(databaseUrl.substring(5)).getPath().substring(1);}
    private void prepareDatabase(String testDatabase) throws Exception {
        URI uri=URI.create(databaseUrl.substring(5));
        if(!List.of("127.0.0.1","localhost","[::1]","::1").contains(uri.getHost()) || uri.getPort()!=5433
                || liveDatabase().equals(testDatabase) || !testDatabase.matches("s4092_review_[a-f0-9]{32}")) throw new IllegalStateException("Requires separate local PostgreSQL 5433 database");
        String url="jdbc:postgresql://127.0.0.1:5433/postgres";
        try(var connection=DriverManager.getConnection(url,username,password)) {
            boolean exists;
            try(var query=connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                query.setString(1,testDatabase);try(var rows=query.executeQuery()){exists=rows.next();}
            }
            if(exists)throw new IllegalStateException("Test database name is already in use");
            try(var statement=connection.createStatement()){statement.execute("CREATE DATABASE "+testDatabase+" TEMPLATE template0");}
        }
    }
    private void dropDatabase(String name) throws Exception {
        if(!name.matches("s4092_review_[a-f0-9]{32}") || name.equals(liveDatabase()))throw new IllegalStateException("Invalid test database");
        try(var connection=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5433/postgres",username,password);var statement=connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS "+name+" WITH (FORCE)");
        }
    }
    @PreDestroy public void close() {
        executor.shutdownNow();Process process=child;
        if(process!=null){process.descendants().forEach(ProcessHandle::destroy);process.destroy();}
    }
}
