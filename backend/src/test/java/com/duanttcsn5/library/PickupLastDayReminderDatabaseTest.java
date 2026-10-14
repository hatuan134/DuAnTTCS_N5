package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in: create a separate database; never run migrations or fixtures on the user's real database. */
@EnabledIfEnvironmentVariable(named="S4_06_2_DB_TEST",matches="true")
class PickupLastDayReminderDatabaseTest {
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager manager;
    private ReadyPickupNotificationRepository repository;
    private ReadyPickupNotificationService service;
    private EmailService emails;
    private long reader,book,copy,reservation;
    private static final LocalDate DAY=LocalDate.of(2026,10,15);
    private static final OffsetDateTime MORNING=OffsetDateTime.parse("2026-10-15T07:00:00+07:00");
    private static final OffsetDateTime DEADLINE=OffsetDateTime.parse("2026-10-15T17:00:00+07:00");
    @BeforeAll static void database(){
        String url=System.getenv("S4_06_2_TEST_DB_URL");
        assertNotNull(url,"Set S4_06_2_TEST_DB_URL.");
        assertTrue(url.contains("/library_management_s4062_test"),"Use the isolated library_management_s4062_test database.");
        var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("S4_06_2_TEST_DB_USER","postgres"),
                System.getenv().getOrDefault("S4_06_2_TEST_DB_PASSWORD",""));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
    }
    @BeforeEach void fixture(){
        repository=new ReadyPickupNotificationRepository(jdbc);emails=mock(EmailService.class);
        service=new ReadyPickupNotificationService(repository,emails,manager,Runnable::run,"Quầy số 1",Clock.fixed(MORNING.toInstant(),ZoneOffset.UTC));
        String suffix=UUID.randomUUID().toString();
        reader=jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,?,?,'ACTIVE' FROM roles WHERE code='READER' RETURNING id",Long.class,"Bạn đọc A",suffix+"@example.com");
        book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT MIN(id) FROM authors),(SELECT MIN(id) FROM categories)) RETURNING id",Long.class,"Cơ sở dữ liệu");
        copy=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT MIN(id) FROM shelves),'HELD',CURRENT_DATE,50000,'GOOD') RETURNING id",Long.class,book,"S4062-"+suffix);
        reservation=jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,hold_started_at,pickup_deadline) VALUES (?,?,?,'READY_FOR_PICKUP',?,?,?) RETURNING id",
                Long.class,book,reader,copy,MORNING.minusDays(1),MORNING.minusDays(1),DEADLINE);
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM ready_pickup_notification_attempts WHERE notification_id IN (SELECT id FROM ready_pickup_notifications WHERE reservation_id=?)",reservation);
        jdbc.update("DELETE FROM ready_pickup_notifications WHERE reservation_id=?",reservation);
        jdbc.update("DELETE FROM book_reservations WHERE id=?",reservation);jdbc.update("DELETE FROM book_copies WHERE id=?",copy);
        jdbc.update("DELETE FROM books WHERE id=?",book);jdbc.update("DELETE FROM users WHERE id=?",reader);
    }
    private ReadyPickupNotificationRepository.ReminderCandidate candidate(){
        return repository.reminderCandidates(DAY.atStartOfDay(ReadyPickupNotificationService.ZONE).toOffsetDateTime(),
                DAY.plusDays(1).atStartOfDay(ReadyPickupNotificationService.ZONE).toOffsetDateTime()).stream()
                .filter(r->r.reservationId()==reservation).findFirst().orElseThrow();
    }
    private boolean enqueue(){return Boolean.TRUE.equals(new TransactionTemplate(manager).execute(tx->repository.enqueueReminder(candidate(),MORNING,"Quầy số 1")));}
    @Test void sameReservationDeadlineCanBeEnqueuedOnlyOnce(){
        assertTrue(enqueue());assertFalse(enqueue());
        assertEquals(1,repository.history(reservation).stream().filter(n->"LAST_DAY_REMINDER".equals(n.notificationType())).count());
    }
    @Test void concurrentSchedulesInsertExactlyOneReminder() throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<Boolean> call=()->{start.await();return enqueue();};var a=pool.submit(call);var b=pool.submit(call);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentWorkersSendOneEmailAndRecordOneSuccessfulAttempt() throws Exception{
        enqueue();AtomicInteger calls=new AtomicInteger();
        doAnswer(a->{calls.incrementAndGet();Thread.sleep(100);return null;}).when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->service.processLastDayOne(reservation,null));var b=pool.submit(()->service.processLastDayOne(reservation,null));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals(1,calls.get());var result=repository.history(reservation).stream().filter(n->"LAST_DAY_REMINDER".equals(n.notificationType())).findFirst().orElseThrow();
        assertEquals("SENT",result.status());assertNotNull(result.sentAt());assertEquals(1,result.attempts().size());assertTrue(result.emailContent().contains("Cơ sở dữ liệu"));
    }
    @Test void readyAndReminderHistoryCoexistWithoutInvalidatingTheReadyEvent(){
        var ready=new TransactionTemplate(manager).execute(tx->repository.lockNext(reservation,OffsetDateTime.now()).orElseThrow());
        enqueue();assertTrue(repository.isLatest(ready));
        var history=repository.history(reservation);assertEquals(2,history.size());
        assertTrue(history.stream().anyMatch(n->"READY_FOR_PICKUP".equals(n.notificationType())));
        assertTrue(history.stream().anyMatch(n->"LAST_DAY_REMINDER".equals(n.notificationType())));
    }
    @Test void changedDeadlineAfterEnqueueSkipsSmtp(){
        enqueue();jdbc.update("UPDATE book_reservations SET pickup_deadline=? WHERE id=?",DEADLINE.plusDays(1),reservation);
        service.processLastDayOne(reservation,null);verifyNoInteractions(emails);
        assertEquals("PICKUP_DEADLINE_CHANGED",repository.history(reservation).get(0).errorCode());
    }
    @Test void cancellationAfterEnqueueSkipsSmtp(){
        enqueue();jdbc.update("UPDATE book_reservations SET status='CANCELLED' WHERE id=?",reservation);
        service.processLastDayOne(reservation,null);verifyNoInteractions(emails);
        assertEquals("SKIPPED",repository.history(reservation).get(0).status());
    }
    @Test void expiredBeforeSevenNeverEnqueues(){
        jdbc.update("UPDATE book_reservations SET pickup_deadline=? WHERE id=?",MORNING.minusSeconds(1),reservation);
        assertFalse(enqueue());verifyNoInteractions(emails);
    }
    @Test void previewHasNoDatabaseOrSmtpSideEffects(){
        int count=repository.history(reservation).size();service.previewLastDay(DAY);service.previewLastDay(DAY.plusDays(1));
        assertEquals(count,repository.history(reservation).size());verifyNoInteractions(emails);
    }
    @Test void reminderMigrationPreservesReadyFiveMinuteConstraint(){
        var ready=repository.history(reservation).get(0);
        assertEquals(300000,Duration.between(ready.eventAt(),ready.sendBefore()).toMillis());
        enqueue();var reminder=repository.history(reservation).get(0);assertEquals(DEADLINE.toInstant(),reminder.sendBefore().toInstant());
    }
}
