package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * S2_07_3_DB_TEST=true opts in on a dedicated PostgreSQL test database.
 * Sequential fixtures roll back; concurrent fixtures commit and are deleted in
 * finally. Migrations remain and sequences may advance. Requires seeded master data.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_07_3_DB_TEST", matches = "true")
@Transactional
class BookReservationLimitsDatabaseTest {
    @Autowired private BookReservationService service;
    @Autowired private BookReservationRepository reservations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    private record Fixture(Long readerId, List<Long> bookIds) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long readerId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "S2073 " + suffix, suffix + "@example.invalid");
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, expires_at, status)
                VALUES (?, ?, (SELECT MIN(id) FROM card_types), CURRENT_DATE + 30, 'ACTIVE')
                """, "S2073-" + suffix, readerId);
        var titles = new java.util.ArrayList<Long>();
        for (int i = 0; i < 4; i++) titles.add(jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S2073 sách " + i + " " + suffix));
        return new Fixture(readerId, List.copyOf(titles));
    }

    private Long copy(Long bookId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                    received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), ?, CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, bookId, "S2073-" + UUID.randomUUID(), status);
    }

    private Long add(Fixture f, int title, String status) {
        Long copyId = "READY_FOR_PICKUP".equals(status) ? copy(f.bookIds().get(title), "HELD") : null;
        OffsetDateTime time = OffsetDateTime.now().minusDays(10);
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, f.bookIds().get(title), f.readerId(), copyId, status, time,
                copyId == null ? null : time.plusDays(3));
    }

    @Test void activeCountUsesBothStatusesEvenWhenReadyDeadlineHasPassed() {
        Fixture own = fixture(), other = fixture();
        add(own, 0, "PENDING"); add(own, 1, "READY_FOR_PICKUP");
        for (String terminal : List.of("CANCELLED", "EXPIRED", "FULFILLED")) add(own, 2, terminal);
        add(other, 0, "PENDING");
        assertThat(reservations.countActiveForReader(own.readerId())).isEqualTo(2L);
        for (int title = 0; title < 2; title++) assertThat(
                reservations.existsActiveForReaderAndBook(own.readerId(), own.bookIds().get(title))).isTrue();
        assertThat(reservations.existsActiveForReaderAndBook(own.readerId(), own.bookIds().get(2))).isFalse();
        assertThat(reservations.existsActiveForReaderAndBook(other.readerId(), own.bookIds().get(0))).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void zeroOneOrTwoActiveAllowNewReservation(int count) {
        Fixture own = fixture();
        for (int i = 0; i < count; i++) add(own, i, i == 1 ? "READY_FOR_PICKUP" : "PENDING");
        assertThat(service.reserve(own.bookIds().get(3), own.readerId()).status()).isEqualTo("PENDING");
        assertThat(reservations.countActiveForReader(own.readerId())).isEqualTo(count + 1L);
    }

    @Test void threeActiveRejectFourthAndPreserveQueueAndAvailableCopy() {
        Fixture own = fixture(); Long head = add(own, 0, "PENDING");
        add(own, 1, "READY_FOR_PICKUP"); add(own, 2, "PENDING");
        Long title = own.bookIds().get(3), available = copy(title, "AVAILABLE");
        long before = total(own);
        assertThatThrownBy(() -> service.reserve(title, own.readerId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RESERVATION_LIMIT_REACHED"));
        assertThat(total(own)).isEqualTo(before);
        assertThat(reservations.countPendingQueueByBookId(title)).isZero();
        assertThat(reservations.findPendingQueuePosition(head)).isEqualTo(1L);
        assertThat(copyStatus(available)).isEqualTo("AVAILABLE");
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "READY_FOR_PICKUP"})
    void duplicateActiveTitleRejectsWithoutQueueOrCopyChanges(String status) {
        Fixture own = fixture(); Long existing = add(own, 0, status), title = own.bookIds().get(0);
        Long available = copy(title, "AVAILABLE");
        long before = total(own), queue = reservations.countPendingQueueByBookId(title);
        assertThatThrownBy(() -> service.reserve(title, own.readerId()))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RESERVATION_ALREADY_ACTIVE");
                    assertThat(error.getMessage()).contains(jdbc.queryForObject(
                            "SELECT title FROM books WHERE id = ?", String.class, title));
                });
        assertThat(total(own)).isEqualTo(before);
        assertThat(reservations.countPendingQueueByBookId(title)).isEqualTo(queue);
        if ("PENDING".equals(status)) assertThat(reservations.findPendingQueuePosition(existing)).isEqualTo(1L);
        assertThat(copyStatus(available)).isEqualTo("AVAILABLE");
        if ("READY_FOR_PICKUP".equals(status)) assertThat(jdbc.queryForObject("""
                SELECT bc.status FROM book_copies bc JOIN book_reservations r ON r.book_copy_id = bc.id
                WHERE r.id = ?
                """, String.class, existing)).isEqualTo("HELD");
    }

    @ParameterizedTest @ValueSource(strings = {"CANCELLED", "EXPIRED", "FULFILLED"})
    void terminalReservationDoesNotBlockSameTitle(String status) {
        Fixture own = fixture(); add(own, 0, status);
        assertThat(service.reserve(own.bookIds().get(0), own.readerId()).status()).isEqualTo("PENDING");
        assertThat(total(own)).isEqualTo(2L);
        assertThat(reservations.countActiveForReader(own.readerId())).isEqualTo(1L);
    }

    @Test void cancelThenReserveSameTitleAgainUsesExistingCancellationFlow() {
        Fixture own = fixture(); Long previous = add(own, 0, "PENDING");
        service.cancelMine(previous, own.readerId());
        assertThat(service.reserve(own.bookIds().get(0), own.readerId()).status()).isEqualTo("PENDING");
        assertThat(reservations.countActiveForReader(own.readerId())).isEqualTo(1L);
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDifferentTitlesAtTwoActiveCannotReachFour() throws Exception { concurrentCreation(false); }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSameTitleCannotCreateDuplicate() throws Exception { concurrentCreation(true); }

    private void concurrentCreation(boolean sameTitle) throws Exception {
        Fixture own = fixture(); ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch saved = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        try {
            if (!sameTitle) { add(own, 0, "PENDING"); add(own, 1, "PENDING"); }
            Long firstTitle = own.bookIds().get(2), secondTitle = own.bookIds().get(sameTitle ? 2 : 3);
            TransactionTemplate tx = new TransactionTemplate(transactions);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            Future<String> first = pool.submit(() -> tx.execute(transaction -> {
                service.reserve(firstTitle, own.readerId()); saved.countDown(); await(release);
                return "CREATED";
            }));
            assertThat(saved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> second = pool.submit(() -> {
                try {
                    return tx.execute(transaction -> {
                        secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        started.countDown();
                        service.reserve(secondTitle, own.readerId());
                        return "CREATED";
                    });
                } catch (ApiException error) { return error.getCode(); }
            });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            // Observe the actual database wait before releasing the first transaction.
            // This test fails if the reader lock is removed, rather than depending on scheduling.
            awaitReaderLockWait(secondPid.get(), second);
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo("CREATED");
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(
                    sameTitle ? "RESERVATION_ALREADY_ACTIVE" : "RESERVATION_LIMIT_REACHED");
            assertThat(reservations.countActiveForReader(own.readerId())).isEqualTo(sameTitle ? 1L : 3L);
            assertThat(total(own)).isEqualTo(sameTitle ? 1L : 3L);
        } finally {
            release.countDown(); pool.shutdownNow();
            if (!pool.awaitTermination(20, TimeUnit.SECONDS))
                throw new IllegalStateException("Worker chưa dừng; kiểm tra fixture S2073 trên DB test.");
            jdbc.update("DELETE FROM book_reservations WHERE reader_id = ?", own.readerId());
            for (Long title : own.bookIds()) {
                jdbc.update("DELETE FROM book_copies WHERE book_id = ?", title);
                jdbc.update("DELETE FROM books WHERE id = ?", title);
            }
            jdbc.update("DELETE FROM library_cards WHERE user_id = ?", own.readerId());
            jdbc.update("DELETE FROM users WHERE id = ?", own.readerId());
        }
    }

    private void awaitReaderLockWait(int pid, Future<?> worker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pg_locks
                    WHERE locktype = 'advisory' AND pid = ? AND NOT granted
                    """, Long.class, pid);
            if (waiting != null && waiting > 0) return;
            if (worker.isDone()) throw new AssertionError("Yêu cầu thứ hai không chờ khóa theo bạn đọc.");
            Thread.sleep(20);
        }
        throw new AssertionError("Không quan sát được yêu cầu thứ hai chờ advisory lock.");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout chờ transaction test.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(error);
        }
    }

    private long total(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM book_reservations WHERE reader_id = ?", Long.class, f.readerId());
    }
    private String copyStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, id);
    }
}
