package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in, fixed disposable localhost database only. Never reads application datasource settings. */
@EnabledIfSystemProperty(named="reminder.pg.test", matches="true")
class LoanReminderPostgresTest {
    static String schema;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    LoanReminderRepository repo;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:55439/reminder_reliability_test?currentSchema="+schema,"reminder_test",""); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean LoanReminderRepository repository(JdbcTemplate jdbc) { return new LoanReminderRepository(jdbc); }
    }
    void migration(String name) throws Exception { jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/"+name))); }
    @BeforeEach void setup() throws Exception {
        schema="reminder_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/reminder_reliability_test","reminder_test",""));
        admin.execute("CREATE SCHEMA "+schema);
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=context.getBean(JdbcTemplate.class); repo=context.getBean(LoanReminderRepository.class);
        jdbc.execute("CREATE TABLE users(id bigint PRIMARY KEY, full_name text, email text); INSERT INTO users VALUES (1,'Bạn đọc','reader@example.test')");
        migration("V49__auto_email_reminders_before_and_on_due_date.sql");
        migration("V50__allow_overdue_reminder_groups.sql");
        migration("V51__auto_retry_failed_reminders_and_audit_logs.sql");
        jdbc.execute("""
            CREATE TABLE books(id bigint PRIMARY KEY, title text);
            CREATE TABLE book_copies(id bigint PRIMARY KEY, book_id bigint, barcode text, status text);
            CREATE TABLE loans(id bigint PRIMARY KEY, loan_number text, borrower_user_id bigint);
            CREATE TABLE library_cards(user_id bigint, card_number text);
            CREATE TABLE loan_items(id bigint PRIMARY KEY, loan_id bigint, book_copy_id bigint,
                due_date timestamptz, returned_at timestamptz, resolution_reason text);
            INSERT INTO books VALUES (1,'Sách tiếng Việt');
            INSERT INTO book_copies VALUES (1,1,'BC-1','BORROWED');
            INSERT INTO loans VALUES (1,'PM-1',1);
            """);
    }
    @AfterEach void close() {
        if(context!=null) context.close(); // Keep isolated schemas for inspection; disposable cluster is removed by operator.
    }
    LoanReminderGroupDto group(String kind) {
        return new LoanReminderGroupDto(1L,"Bạn đọc","reader@example.test",kind,
                List.of(new LoanReminderItemDto(1L,1L,"PM-1",1L,"Sách","BC-1",today.atStartOfDay().atOffset(ZoneOffset.ofHours(7)),1,0)),
                0,"Subject","HTML",false);
    }
    Long old(String kind,String status) {
        return repo.insertLog(today,1L,"Bạn đọc","reader@example.test",kind,1,0,"PM-1","Sách","[]","Subject","HTML",
                status,null,"SYSTEM",null,null,false);
    }
    @Test void migrationPreservesDuplicateHistoryAndCanonicalizesAllLegacyGroups() throws Exception {
        for(String g:List.of("OVERDUE_1_DAY","OVERDUE_7_DAYS","OVERDUE_30_DAYS")) {old(g,"SENT");old(g,"FAILED_MAX_RETRIES");}
        old("DUE_TODAY","PENDING_RETRY");
        migration("V65__loan_reminder_delivery_claims.sql");
        assertEquals(7,jdbc.queryForObject("SELECT count(*) FROM loan_reminder_logs",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM loan_reminder_logs WHERE status='FAILED_MAX_RETRIES'",Integer.class));
        assertEquals("SENT",repo.deliveryState(today,1L,"OVERDUE_DAILY"));
        assertEquals("REVIEW_REQUIRED",repo.deliveryState(today,1L,"DUE_TODAY"));
        assertNull(repo.claimInitial(today,group("OVERDUE_DAILY"),"[]","SYSTEM",null,null));
        assertFalse(repo.claimRetry(7L,0));
    }
    @Test void twoDatabaseConnectionsClaimOnceAndNextDayCanClaim() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        var gate=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        Callable<Long> task=()->{gate.await();return repo.claimInitial(today,group("OVERDUE_DAILY"),"[]","SYSTEM",null,null);};
        try {
            var a=pool.submit(task);var b=pool.submit(task);
            assertEquals(1,(a.get()!=null?1:0)+(b.get()!=null?1:0));
            assertNotNull(repo.claimInitial(today.plusDays(1),group("OVERDUE_DAILY"),"[]","SYSTEM",null,null));
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentServicesSendSmtpExactlyOnceForManualAndScheduler() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        jdbc.update("INSERT INTO loan_items VALUES (1,1,1,?,NULL,NULL)",today.atTime(17,0).atOffset(ZoneOffset.ofHours(7)));
        var mail=mock(EmailService.class);
        var a=new LoanReminderService(repo,mail,new ObjectMapper());
        var b=new LoanReminderService(repo,mail,new ObjectMapper());
        var gate=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var x=pool.submit(()->{gate.await();return a.processDailyReminders(today,"MANUAL",1L,"A");});
            var y=pool.submit(()->{gate.await();return b.processDailyReminders(today,"SYSTEM",null,null);});
            assertEquals(1,x.get().sentCount()+y.get().sentCount());
            verify(mail,times(1)).sendRawHtmlEmail(any(),any(),any());
            assertEquals("SENT",repo.deliveryState(today,1L,"DUE_TODAY"));
        } finally {pool.shutdownNow();}
    }
    @Test void retryCasAndSentCheckPreventResend() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        Long id=repo.claimInitial(today,group("DUE_TODAY"),"[]","SYSTEM",null,null);
        assertNull(repo.claimInitial(today,group("DUE_TODAY"),"[]","MANUAL",1L,"A"));
        repo.finishDelivery(id,"PENDING_RETRY",0,"SMTP_CONNECT","SYSTEM");
        assertNull(repo.claimInitial(today,group("DUE_TODAY"),"[]","MANUAL",1L,"A"));
        var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> task=()->{gate.await();return repo.claimRetry(id,0);};
        try {var a=pool.submit(task);var b=pool.submit(task);assertNotEquals(a.get(),b.get());}
        finally {pool.shutdownNow();}
        repo.finishDelivery(id,"SENT",1,null,"RETRY");
        assertFalse(repo.claimRetry(id,1));
    }
    @Test void failedCompletionRollsBackAndKeepsClaimBlocked() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        Long id=repo.claimInitial(today,group("DUE_TODAY"),"[]","SYSTEM",null,null);
        // Historical unique SENT guard must remain enforced.
        old("DUE_TODAY","SENT");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->repo.finishDelivery(id,"SENT",0,null,"SYSTEM"));
        assertEquals("PROCESSING",repo.deliveryState(today,1L,"DUE_TODAY"));
        assertFalse(repo.claimRetry(id,0));
    }
    @Test void queryIncludesEveryOverdueDayAndExcludesResolvedAndNonBorrowedCopies() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        int id=0;
        for(int days:List.of(-2,0,1,2,7,30,31,100)) jdbc.update("INSERT INTO loan_items VALUES (?,1,1,?,NULL,NULL)",++id,today.minusDays(days).atTime(17,0).atOffset(ZoneOffset.ofHours(7)));
        assertEquals(8,repo.findEligibleItemsForDate(today).size());
        jdbc.update("UPDATE loan_items SET returned_at=now() WHERE id=1");
        jdbc.update("UPDATE loan_items SET resolution_reason='LOST' WHERE id=2");
        jdbc.update("UPDATE loan_items SET resolution_reason='DAMAGED' WHERE id=3");
        assertEquals(5,repo.findEligibleItemsForDate(today).size());
        jdbc.update("UPDATE book_copies SET status='AVAILABLE'");
        assertTrue(repo.findEligibleItemsForDate(today).isEmpty());
    }
    @Test void retryChecksCurrentLoanStateAndOriginalDueDate() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        var due=today.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
        jdbc.update("INSERT INTO loan_items VALUES (1,1,1,?,NULL,NULL)",due);
        var group=group("DUE_TODAY");
        Long id=repo.claimInitial(today,group,new ObjectMapper().writeValueAsString(group.items()),"SYSTEM",null,null);
        assertTrue(repo.retryItemsStillEligible(id));
        jdbc.update("UPDATE loan_items SET resolution_reason='LOST'");
        assertFalse(repo.retryItemsStillEligible(id));
        jdbc.update("UPDATE loan_items SET resolution_reason='DAMAGED'");
        assertFalse(repo.retryItemsStillEligible(id));
        jdbc.update("UPDATE loan_items SET resolution_reason=NULL, returned_at=now()");
        assertFalse(repo.retryItemsStillEligible(id));
        jdbc.update("UPDATE loan_items SET returned_at=NULL, due_date=due_date + INTERVAL '1 day'");
        assertFalse(repo.retryItemsStillEligible(id));
    }
    @Test void concurrentHttpRequestsFromTwoStaffOrDoubleClickSendOnce() throws Exception {
        migration("V65__loan_reminder_delivery_claims.sql");
        jdbc.update("INSERT INTO loan_items VALUES (1,1,1,?,NULL,NULL)",today.atTime(17,0).atOffset(ZoneOffset.ofHours(7)));
        var mail=mock(EmailService.class);
        var service=new LoanReminderService(repo,mail,new ObjectMapper());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.duanttcsn5.library.controller.LoanReminderController(service)).build();
        var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> request=()->{gate.await();return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/loan-reminders/trigger").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"targetDate\":\""+today+"\"}"))
                .andReturn().getResponse().getStatus();};
        try {
            var a=pool.submit(request);var b=pool.submit(request);
            assertEquals(200,a.get());assertEquals(200,b.get());
            verify(mail,times(1)).sendRawHtmlEmail(any(),any(),any());
        } finally {pool.shutdownNow();}
    }
    @Test void historyFiltersMatchDisplayedOutcomesAndIncludeLegacyOverdueGroups() throws Exception {
        old("OVERDUE_1_DAY", "SENT");
        old("OVERDUE_7_DAYS", "FAILED_MAX_RETRIES");
        old("OVERDUE_30_DAYS", "PENDING_RETRY");
        old("DUE_TODAY", "FAILED");
        migration("V65__loan_reminder_delivery_claims.sql");
        old("OVERDUE_DAILY", "REVIEW_REQUIRED");
        old("OVERDUE_DAILY", "PROCESSING");
        assertEquals(5, repo.countLogs(today, "OVERDUE_DAILY", null));
        assertEquals(4, repo.countLogs(today, "OVERDUE_DAILY", "FAILED"));
        var failed = repo.findLogs(today, "OVERDUE_DAILY", "FAILED", 0, 15);
        assertEquals(4, failed.size());
        assertTrue(failed.stream().noneMatch(row -> row.status().equals("SENT")));
        assertEquals(1, repo.countLogs(today, "OVERDUE_DAILY", "SENT"));
        assertEquals(1, repo.findLogs(today, "OVERDUE_DAILY", "SENT", 0, 15).size());
        assertEquals(1, repo.countLogs(today, "DUE_TODAY", "FAILED"));
    }
}
