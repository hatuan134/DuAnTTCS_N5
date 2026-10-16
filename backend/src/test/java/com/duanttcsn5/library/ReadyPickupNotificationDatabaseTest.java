package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mail.MailSendException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs only against a separate test database; never uses the real project's .env. */
@EnabledIfEnvironmentVariable(named="S4_06_1_DB_TEST",matches="true")
class ReadyPickupNotificationDatabaseTest {
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager manager;
    private ReadyPickupNotificationRepository repository;
    private EmailService emails;
    private ReadyPickupNotificationService service;
    private long reader,book,copy,reservation;
    @BeforeAll static void database() {
        String url=System.getenv("S4_06_1_TEST_DB_URL");
        assertNotNull(url,"Set S4_06_1_TEST_DB_URL to an isolated test database.");
        assertTrue(url.contains("library_management_s4061_test"),"Use a separate library_management_s4061_test database.");
        var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("S4_06_1_TEST_DB_USER","postgres"),
                System.getenv().getOrDefault("S4_06_1_TEST_DB_PASSWORD",""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
    }
    @BeforeEach void fixture() {
        repository=new ReadyPickupNotificationRepository(jdbc);emails=mock(EmailService.class);
        service=new ReadyPickupNotificationService(repository,emails,manager,Runnable::run,"Quầy nhận sách số 1",Clock.systemUTC());
        String suffix=UUID.randomUUID().toString();
        reader=jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,?,?, 'ACTIVE' FROM roles WHERE code='READER' RETURNING id",Long.class,"Bạn đọc A",suffix+"@example.com");
        book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT MIN(id) FROM authors),(SELECT MIN(id) FROM categories)) RETURNING id",Long.class,"Lập trình Java");
        copy=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT MIN(id) FROM shelves),'AVAILABLE',CURRENT_DATE,50000,'GOOD') RETURNING id",Long.class,book,"S4061-"+suffix);
        reservation=jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id,status,reserved_at) VALUES (?,?,'PENDING',CURRENT_TIMESTAMP - INTERVAL '1 day') RETURNING id",Long.class,book,reader);
    }
    private void ready() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            jdbc.update("UPDATE book_copies SET status='HELD' WHERE id=?",copy);
            jdbc.update("UPDATE book_reservations SET status='READY_FOR_PICKUP',book_copy_id=?,hold_started_at=CURRENT_TIMESTAMP,pickup_deadline=CURRENT_TIMESTAMP + INTERVAL '3 days' WHERE id=? AND status='PENDING'",copy,reservation);
        });
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM ready_pickup_notification_attempts WHERE notification_id IN (SELECT id FROM ready_pickup_notifications WHERE reservation_id=?)",reservation);
        jdbc.update("DELETE FROM ready_pickup_notifications WHERE reservation_id=?",reservation);
        jdbc.update("DELETE FROM book_reservations WHERE id=?",reservation);
        jdbc.update("DELETE FROM book_copies WHERE id=?",copy);
        jdbc.update("DELETE FROM books WHERE id=?",book);
        jdbc.update("DELETE FROM users WHERE id=?",reader);
    }
    @Test void pendingDoesNotEnqueueAndTransitionRecordsExactlyFiveMinuteWindow() {
        assertTrue(repository.history(reservation).isEmpty());ready();
        var row=repository.history(reservation).get(0);
        assertEquals("READY_FOR_PICKUP",row.notificationType());assertEquals("Lập trình Java",row.bookTitle());
        assertEquals(300000,Duration.between(row.eventAt(),row.sendBefore()).toMillis());assertNull(row.sentAt());
    }
    @Test void repeatedStatusUpdateDoesNotDuplicate() {
        ready();jdbc.update("UPDATE book_reservations SET status='READY_FOR_PICKUP' WHERE id=?",reservation);
        assertEquals(1,repository.history(reservation).size());
    }
    @Test void insertingAnAlreadyAllocatedReadyOrderAlsoEnqueues() {
        jdbc.update("DELETE FROM book_reservations WHERE id=?",reservation);
        jdbc.update("UPDATE book_copies SET status='HELD' WHERE id=?",copy);
        reservation=jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,hold_started_at,pickup_deadline) VALUES (?,?,?,'READY_FOR_PICKUP',CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '3 days') RETURNING id",Long.class,book,reader,copy);
        assertEquals(1,repository.history(reservation).size());
    }
    @Test void rollbackDoesNotLeaveAnEmailEvent() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            jdbc.update("UPDATE book_copies SET status='HELD' WHERE id=?",copy);
            jdbc.update("UPDATE book_reservations SET status='READY_FOR_PICKUP',book_copy_id=?,hold_started_at=CURRENT_TIMESTAMP,pickup_deadline=CURRENT_TIMESTAMP+INTERVAL '3 days' WHERE id=?",copy,reservation);
            tx.setRollbackOnly();
        });
        assertTrue(repository.history(reservation).isEmpty());assertEquals("PENDING",repository.reservationStatus(reservation).orElseThrow());
    }
    @Test void twoConcurrentUpdatesGenerateOneEvent() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Void> update=()->{start.await();ready();return null;};
            Future<Void> one=pool.submit(update),two=pool.submit(update);start.countDown();one.get(10,TimeUnit.SECONDS);two.get(10,TimeUnit.SECONDS);
            assertEquals(1,repository.history(reservation).size());
        } finally {pool.shutdownNow();}
    }
    @Test void twoWorkersAndRepeatedTriggerSendOnlyOnceAndPersistHistory() throws Exception {
        ready();AtomicInteger count=new AtomicInteger();
        doAnswer(a->{count.incrementAndGet();Thread.sleep(100);return null;}).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one=pool.submit(()->service.processOne(reservation,false,null));
            Future<Boolean> two=pool.submit(()->service.processOne(reservation,false,null));
            one.get(10,TimeUnit.SECONDS);two.get(10,TimeUnit.SECONDS);
            assertFalse(service.processOne(reservation,false,null));assertEquals(1,count.get());
            var log=repository.history(reservation).get(0);assertEquals("SENT",log.status());
            assertNotNull(log.sentAt());assertFalse(log.sentAt().isAfter(log.sendBefore()));assertEquals(1,log.attempts().size());
            assertTrue(log.emailContent().contains("Quầy nhận sách số 1"));
        } finally {pool.shutdownNow();}
    }
    @Test void cancellationBeforeSendSkipsWithoutSmtp() {
        ready();jdbc.update("UPDATE book_reservations SET status='CANCELLED' WHERE id=?",reservation);
        service.processOne(reservation,false,null);verifyNoInteractions(emails);
        assertEquals("SKIPPED",repository.history(reservation).get(0).status());
    }
    @Test void temporaryFailureThenRetryPreservesOneEventAndTwoAttemptRecords() throws Exception {
        ready();doThrow(new MailSendException("connection failed",new java.net.ConnectException())).doNothing().when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(reservation,false,null);
        var first=repository.history(reservation).get(0);assertEquals("RETRY",first.status());assertNull(first.sentAt());
        jdbc.update("UPDATE ready_pickup_notifications SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",first.id());
        service.processOne(reservation,false,null);
        var finalLog=repository.history(reservation).get(0);assertEquals("SENT",finalLog.status());assertEquals(first.eventAt(),finalLog.eventAt());
        assertEquals(first.sendBefore(),finalLog.sendBefore());assertEquals(2,finalLog.attempts().size());
    }
    @Test void committedClaimSurvivesCrashAndIsNotRetriedAsUnsent() {
        ready();new TransactionTemplate(manager).executeWithoutResult(tx->{
            var event=repository.lockNext(reservation,OffsetDateTime.now()).orElseThrow();repository.claim(event,OffsetDateTime.now(),false,null);
        });
        assertEquals("SENDING",repository.history(reservation).get(0).status());assertFalse(service.processOne(reservation,false,null));
        new TransactionTemplate(manager).executeWithoutResult(tx->repository.failAbandoned(OffsetDateTime.now().plusMinutes(6)));
        var log=repository.history(reservation).get(0);assertEquals("FAILED",log.status());assertEquals("DELIVERY_UNCERTAIN",log.errorCode());
        assertEquals("FAILED",log.attempts().get(0).outcome());verifyNoInteractions(emails);
    }
}
