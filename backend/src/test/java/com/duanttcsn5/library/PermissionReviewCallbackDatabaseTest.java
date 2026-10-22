package com.duanttcsn5.library;

import com.duanttcsn5.library.service.PermissionReviewGitHubCallbackService;
import com.duanttcsn5.library.service.PermissionReviewGitHubCallbackService.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.bootstrap-admin.password=","app.scheduling.enabled=false",
        "app.permission-review.launch-enabled=false","app.pickup-email.automatic-enabled=false",
        "spring.main.allow-bean-definition-overriding=true","app.permission-review.github.owner=owner",
        "app.permission-review.github.repository=repo",
        "app.permission-review.github.callback-secret=database-test-secret-never-production-4092"})
@EnabledIfEnvironmentVariable(named="S4_09_2_DB_TEST",matches="true")
@ContextConfiguration(initializers=FullPermissionDatabaseTest.DatabaseGuard.class)
@Import(FullPermissionDatabaseTest.SafeTransport.class)
class PermissionReviewCallbackDatabaseTest {
    @Autowired PermissionReviewGitHubCallbackService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test void simultaneousRetriesStoreOneFailedJobAndOneImmutableReceipt() throws Exception {
        UUID key=UUID.randomUUID(); var now=OffsetDateTime.now().minusSeconds(1);
        var ci=new CiRun("owner/repo",Math.abs(key.getMostSignificantBits()),1,"b".repeat(40),"main","schedule",now.minusMinutes(1),now,0,0,0);
        String body=mapper.writeValueAsString(new Callback(key,"a".repeat(64),true,null,ci));
        String stamp=String.valueOf(Instant.now().getEpochSecond());
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("database-test-secret-never-production-4092".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature="sha256="+HexFormat.of().formatHex(mac.doFinal((stamp+"."+body).getBytes(StandardCharsets.UTF_8)));
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Void> callback=()->{gate.await();service.accept(stamp,signature,body);return null;};
            var a=pool.submit(callback); var b=pool.submit(callback); gate.countDown();
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM permission_review_ci_receipts WHERE run_key=?",Integer.class,key));
            assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM permission_review_jobs WHERE run_key=?",String.class,key));
            assertNull(jdbc.queryForObject("SELECT report_id FROM permission_review_jobs WHERE run_key=?",Long.class,key));
            assertThrows(Exception.class,()->jdbc.update("DELETE FROM permission_review_ci_receipts WHERE run_key=?",key));
        } finally {pool.shutdownNow();}
    }
}
