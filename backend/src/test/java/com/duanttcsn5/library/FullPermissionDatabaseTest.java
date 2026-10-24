package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.ai.AiChatService;
import com.duanttcsn5.library.ai.AiTypes;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.mockito.Mockito.*;

/** Full real-controller/service/JWT/PostgreSQL regression. Each run uses a unique disposable database; transactions execute exactly as in the application. */
@SpringBootTest(properties={"app.bootstrap-admin.password=","app.scheduling.enabled=false",
        "app.permission-review.launch-enabled=false","app.pickup-email.automatic-enabled=false","app.deposits.activation-enabled=true",
        "spring.main.allow-bean-definition-overriding=true","app.mail.retry-delay-ms=0","spring.datasource.hikari.connection-init-sql=SET lock_timeout='3s'; SET statement_timeout='15s'"})
@EnabledIfEnvironmentVariable(named="S4_09_2_DB_TEST",matches="true")
@ContextConfiguration(initializers=FullPermissionDatabaseTest.DatabaseGuard.class)
@Import(FullPermissionDatabaseTest.SafeTransport.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPermissionDatabaseTest {
    public static class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            validate(context.getEnvironment().getProperty("spring.datasource.url",""),System.getenv("PERMISSION_REVIEW_LIVE_DB"));
        }
        public static void validate(String url,String liveDatabase) {
            if(!url.startsWith("jdbc:postgresql://"))throw new IllegalStateException("S4-09.2 yêu cầu PostgreSQL thử riêng.");
            java.net.URI uri=java.net.URI.create(url.substring(5));
            String name=Optional.ofNullable(uri.getPath()).orElse("").replaceFirst("^/","");
            if(!List.of("127.0.0.1","localhost").contains(uri.getHost()) || uri.getPort()!=5433
                    || !name.matches("s4092_review_[a-f0-9]{32}") || liveDatabase==null || liveDatabase.isBlank() || name.equals(liveDatabase))
                throw new IllegalStateException("S4-09.2 chỉ chạy trên database thử riêng s4092_review_<UUID> ở localhost:5433, khác database ứng dụng được khai báo.");
        }
    }
    @TestConfiguration
    static class SafeTransport {
        /** The role-matrix suite must not consume real AI quotas or send demo users to a vendor. */
        @Bean @Primary AiChatService permissionReviewAiService() {
            AiChatService ai = mock(AiChatService.class);
            when(ai.chat(any(AiTypes.ChatRequest.class), anyString())).thenReturn(
                    new AiTypes.ChatResponse("00000000-0000-0000-0000-000000000001",
                            "Trả lời thử an toàn", List.of(), "MOCK", false, 0));
            when(ai.listHistory()).thenReturn(List.of());
            return ai;
        }

        @Bean @Primary JavaMailSender permissionReviewMailSender() {
            return (JavaMailSender)java.lang.reflect.Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),new Class[]{JavaMailSender.class},(proxy,method,args)-> {
                if(method.getName().equals("createMimeMessage"))return new MimeMessage(Session.getInstance(new Properties()));
                if(method.getName().equals("toString"))return "S4-09.2 in-memory mail transport";
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                return null;
            });
        }
        @Bean(name="mailTaskExecutor") java.util.concurrent.Executor synchronousMail() {return Runnable::run;}
        @Bean(name="pickupNotificationExecutor") ThreadPoolTaskExecutor synchronousPickup() {
            return new ThreadPoolTaskExecutor(){@Override public void execute(Runnable task){task.run();}};
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PermissionReviewCatalog catalog;
    @Autowired PermissionReviewInspector inspector;
    MockMvc mvc; PermissionReviewApiFixtures fixtures; Connection lock;
    final List<EvidenceCase> checks=new ArrayList<>(); OffsetDateTime started;
    UUID runKey; String fingerprint;
    @BeforeAll void setup() throws Exception {
        started=OffsetDateTime.now();
        runKey=UUID.fromString(Optional.ofNullable(System.getenv("PERMISSION_REVIEW_RUN_KEY")).orElse(UUID.randomUUID().toString()));
        lock=Objects.requireNonNull(jdbc.getDataSource()).getConnection();
        try(var s=lock.createStatement();var rs=s.executeQuery("SELECT pg_try_advisory_lock(4092,1)")){rs.next();assertTrue(rs.getBoolean(1),"Database thử đang được lần kiểm thử khác sử dụng.");}
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        fixtures=new PermissionReviewApiFixtures(context,mvc,jdbc,mapper,catalog,inspector);
        fixtures.users();fingerprint=inspector.inspect().fingerprint();fixtures.fingerprint=fingerprint;
        assertTrue(inspector.inspect().undocumentedApis().isEmpty(),"Có API chưa được quy định trong tài liệu: "+inspector.inspect().undocumentedApis());
    }
    @TestFactory Stream<DynamicTest> everyMethodAndPathWithFourRolesAndAnonymous() {
        String filter=System.getProperty("permission.review.filter","");
        return catalog.policy().entries().stream().filter(e->filter.isBlank() || e.id().matches(filter))
                .flatMap(entry->entry.httpMethods().stream().flatMap(method->Stream.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","ANONYMOUS")
                        .map(role->DynamicTest.dynamicTest(method+" "+entry.id()+" / "+role,()->probe(entry,method,role,"STANDARD")))));
    }
    @TestFactory Stream<DynamicTest> overrideAndOwnershipCannotBeBypassedByChangingIdentifiers() {
        String filter=System.getProperty("permission.review.filter","");
        return catalog.policy().entries().stream().filter(e->filter.isBlank() || e.id().matches(filter)).flatMap(entry->entry.httpMethods().stream().flatMap(method->
                entry.rules().entrySet().stream().filter(rule->Set.of("OWN","OVERRIDE").contains(rule.getValue())).map(rule->{
                    String scenario=rule.getValue().equals("OVERRIDE")?"OVERRIDE":Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine","DepositController.accounts","DepositController.history").contains(entry.id())?"OTHER_OWNER":"SPOOFED_OWNER";
                    return DynamicTest.dynamicTest(method+" "+entry.id()+" / "+rule.getKey()+" / "+scenario,()->probe(entry,method,rule.getKey(),scenario));
                })));
    }
    void probe(PolicyEntry entry,String method,String role,String scenario) throws Exception {
        if(entry.controller().equals("DepositController")) {
            var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            tx.executeWithoutResult(status->{try {probeBody(entry,method,role,scenario);}catch(Exception e){throw new RuntimeException(e);}finally{status.setRollbackOnly();}});
        } else probeBody(entry,method,role,scenario);
    }
    void probeBody(PolicyEntry entry,String method,String role,String scenario) throws Exception {
        System.out.println("CHECK "+entry.id()+" "+method+" "+role+" "+scenario);
        Throwable[] failure={null};
        // The run endpoint legitimately rejects a real request before the configured 07:00 send time.
        // Exercise permission/allowed-path behavior at 08:00 on the SAME Vietnam calendar date,
        // without relaxing the production rule or touching Production data.
        boolean pickupRun = entry.id().equals("PickupLastDayReminderController.run") && method.equals("POST");
        ReadyPickupNotificationService pickupService = pickupRun ? context.getBean(ReadyPickupNotificationService.class) : null;
        Clock previousClock = pickupRun ? (Clock) ReflectionTestUtils.getField(pickupService, "clock") : null;
        try {
            if (pickupRun) {
                ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
                LocalDate runDate = LocalDate.now(vietnam);
                ReflectionTestUtils.setField(pickupService, "clock",
                        Clock.fixed(runDate.atTime(LocalTime.of(8, 0)).atZone(vietnam).toInstant(), vietnam));
            }
            try {
                fixtures.prepare(entry,role,scenario);
                var request=fixtures.request(entry,method,role,scenario);
                int expected=catalog.requiredStatus(entry,role,scenario,fixtures.successStatus(entry));
                String before=fixtures.snapshot(); OffsetDateTime at=OffsetDateTime.now();
                MvcResult result=mvc.perform(request).andReturn();int actual=result.getResponse().getStatus();
                boolean denied=Set.of(401,403,404,405).contains(expected);
                Boolean unchanged=denied?before.equals(fixtures.snapshot()):null;
                boolean assertions=false;
                try {
                    assertEquals(expected,actual,result.getResponse().getContentAsString()+"; resolved="+result.getResolvedException());
                    if(denied)assertEquals(Boolean.TRUE,unchanged,"Yêu cầu bị từ chối đã làm thay đổi dữ liệu.");
                    else fixtures.verify(entry,role,scenario,result);
                    assertions=true;
                } catch(Throwable error){failure[0]=error;}
                var historical=historicalFinding(entry.id(),method,role);
                Integer beforeStatus=historical==null?null:historical.get("observedStatus").asInt();
                OffsetDateTime detected=historical==null?at:OffsetDateTime.parse(historical.get("verifiedAt").asText());
                checks.add(new EvidenceCase(entry.id(),role,scenario,expected,beforeStatus,actual,unchanged,detected,OffsetDateTime.now(),
                        "MockMvc với controller, validation, service, JWT ký thật và PostgreSQL 15; fixture riêng cho từng ca; database tạm bị xóa sau lần chạy. "
                        +method+" "+entry.path()+". Đối chiếu toàn bộ bảng nghiệp vụ khi bị từ chối; email dùng transport trong bộ nhớ."
                        +(entry.module().equals("REVIEW")?" Tác vụ khởi chạy lồng nhau chỉ ghi nhận yêu cầu; không tạo tiến trình Maven con trong test.":""),
                        historical==null?null:historical.get("correction").asText(),method,assertions));
            } catch(Throwable error){failure[0]=error;}
        } finally {
            if (pickupRun) ReflectionTestUtils.setField(pickupService, "clock", previousClock);
        }
        if(failure[0]!=null)throw new AssertionError(entry.id()+" / "+role+" / "+scenario+": "+failure[0].getMessage(),failure[0]);
    }
    tools.jackson.databind.JsonNode historicalFinding(String endpoint,String method,String role) throws Exception {
        try(var input=getClass().getResourceAsStream("/permissions/S4-09.2-fixes.json")) {
            if(input==null)return null;
            for(var item:mapper.readTree(input).get("findings"))
                if(item.get("endpointId").asText().equals(endpoint) && item.get("httpMethod").asText().equals(method) && item.get("role").asText().equals(role))return item;
            return null;
        }
    }
    @AfterAll void evidence() throws Exception {
        try {
            if(fingerprint!=null && !checks.isEmpty()) {
                var report=new ImportReport(2,runKey,catalog.policy().version(),fingerprint,"REGRESSION","ISOLATED_POSTGRES",
                        "FullPermissionDatabaseTest / real JWT + PostgreSQL / disposable database","Bộ kiểm thử toàn hệ thống S4-09.2",started,OffsetDateTime.now(),List.copyOf(checks));
                Path output=Path.of(Optional.ofNullable(System.getenv("PERMISSION_REVIEW_RESULT")).orElse("target/permission-review/full-regression.json"));
                Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            }
        } finally {if(lock!=null)lock.close();}
    }
}
