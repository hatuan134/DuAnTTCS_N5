package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Only an isolated test database is allowed. Never reads the application's .env. */
@EnabledIfEnvironmentVariable(named="S4_06_3_DB_TEST",matches="true")
class PickupCancellationEmailDatabaseTest {
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager manager;
    private long reader,book,copy,reservation;
    private ReadyPickupNotificationRepository repository;
    private ReadyPickupNotificationService service;
    private EmailService emails;
    @BeforeAll static void database() {
        String url=System.getenv("S4_06_3_TEST_DB_URL");assertNotNull(url);
        assertTrue(url.contains("library_management_s4063_test"),"Use an isolated library_management_s4063_test database.");
        var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("S4_06_3_TEST_DB_USER","postgres"),
                System.getenv().getOrDefault("S4_06_3_TEST_DB_PASSWORD",""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
    }
    @BeforeEach void fixture() {
        String suffix=UUID.randomUUID().toString();
        reader=jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,?,?, 'ACTIVE' FROM roles WHERE code='READER' RETURNING id",Long.class,"Bạn đọc A",suffix+"@example.com");
        book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT MIN(id) FROM authors),(SELECT MIN(id) FROM categories)) RETURNING id",Long.class,"Mạng máy tính");
        copy=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT MIN(id) FROM shelves),'HELD',CURRENT_DATE,50000,'GOOD') RETURNING id",Long.class,book,"S4063-"+suffix);
        reservation=jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,hold_started_at,pickup_deadline) VALUES (?,?,?,'READY_FOR_PICKUP',CURRENT_TIMESTAMP-INTERVAL '3 days',CURRENT_TIMESTAMP-INTERVAL '3 days',CURRENT_TIMESTAMP-INTERVAL '1 minute') RETURNING id",Long.class,book,reader,copy);
        repository=new ReadyPickupNotificationRepository(jdbc);emails=mock(EmailService.class);
        service=new ReadyPickupNotificationService(repository,emails,manager,Runnable::run,"",Clock.systemUTC());
    }
    private void cancel() {
        jdbc.update("UPDATE book_reservations SET status='CANCELLED',cancellation_reason=?,cancelled_by=NULL,cancelled_by_name='Hệ thống',cancelled_at=CURRENT_TIMESTAMP WHERE id=?",
                ReservationAutoCancellationService.OVERDUE_CANCELLATION_REASON,reservation);
    }
    private long cancellationCount() {
        return repository.history(reservation).stream().filter(n->ReadyPickupNotificationService.CANCELLATION_TYPE.equals(n.notificationType())).count();
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM ready_pickup_notification_attempts WHERE notification_id IN (SELECT id FROM ready_pickup_notifications WHERE reservation_id=?)",reservation);
        jdbc.update("DELETE FROM ready_pickup_notifications WHERE reservation_id=?",reservation);
        jdbc.update("DELETE FROM book_reservations WHERE id=?",reservation);jdbc.update("DELETE FROM book_copies WHERE id=?",copy);
        jdbc.update("DELETE FROM books WHERE id=?",book);jdbc.update("DELETE FROM users WHERE id=?",reader);
    }
    @Test void cancellationTransitionCreatesSnapshotAndRepeatedUpdateDoesNotDuplicate() {
        cancel();assertEquals(1,cancellationCount());cancel();assertEquals(1,cancellationCount());
        var n=repository.history(reservation).get(0);assertEquals("Mạng máy tính",n.bookTitle());
        assertEquals(book,n.bookId());assertNotNull(n.cancelledAt());assertNull(n.sendBefore());assertNull(n.sentAt());
    }
    @Test void manualCancellationAndOtherReasonsDoNotEnqueue() {
        jdbc.update("UPDATE book_reservations SET status='CANCELLED',cancellation_reason=?,cancelled_by=?,cancelled_by_name='Nhân viên',cancelled_at=CURRENT_TIMESTAMP WHERE id=?",
                ReservationAutoCancellationService.OVERDUE_CANCELLATION_REASON,reader,reservation);
        assertEquals(0,cancellationCount());
    }
    @Test void differentSystemReasonDoesNotEnqueue() {
        jdbc.update("UPDATE book_reservations SET status='CANCELLED',cancellation_reason='Sách bị mất',cancelled_by_name='Hệ thống',cancelled_at=CURRENT_TIMESTAMP WHERE id=?",reservation);
        assertEquals(0,cancellationCount());
    }
    @Test void futureDeadlineAndExpiredButNotCancelledOrdersDoNotEnqueue() {
        assertEquals(0,cancellationCount());
        jdbc.update("UPDATE book_reservations SET pickup_deadline=CURRENT_TIMESTAMP+INTERVAL '1 day' WHERE id=?",reservation);
        cancel();assertEquals(0,cancellationCount());
    }
    @Test void rollbackDoesNotLeaveCancellationEmail() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{ cancel();tx.setRollbackOnly(); });
        assertEquals(0,cancellationCount());assertEquals("READY_FOR_PICKUP",repository.reservationStatus(reservation).orElseThrow());
    }
    @Test void twoConcurrentWorkersSendCancellationOnlyOnce() throws Exception {
        cancel();AtomicInteger sent=new AtomicInteger();
        doAnswer(a->{sent.incrementAndGet();Thread.sleep(50);return null;}).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->service.processOne(null,false,null));var two=pool.submit(()->service.processOne(null,false,null));
            one.get(10,TimeUnit.SECONDS);two.get(10,TimeUnit.SECONDS);
            for (int i=0;i<4 && service.processOne(null,false,null);i++) { }
            assertEquals(1,sent.get());assertEquals(1,cancellationCount());
            var n=repository.history(reservation).get(0);assertEquals("SENT",n.status());assertEquals(1,n.attempts().size());
        } finally { pool.shutdownNow(); }
    }
}
