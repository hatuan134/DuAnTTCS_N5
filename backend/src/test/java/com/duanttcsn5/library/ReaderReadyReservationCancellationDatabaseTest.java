package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.service.BookReservationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL integration checks for S2-08.3.
 * Opt in with S2_08_3_DB_TEST=true using the project's normal local DB environment.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_08_3_DB_TEST", matches = "true")
@Transactional
class ReaderReadyReservationCancellationDatabaseTest {
    @Autowired private BookReservationService service;
    @Autowired private BookReservationRepository reservations;
    @Autowired private BookCopyRepository copies;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private record Fixture(Long bookId, Long readerId) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long readerId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Bạn đọc " + suffix, suffix + "@example.invalid");
        Long bookId = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S2-08.3 " + suffix);
        return new Fixture(bookId, readerId);
    }

    private Long heldCopy(Fixture fixture) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                    received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD')
                RETURNING id
                """, Long.class, fixture.bookId(), "S2083-" + UUID.randomUUID());
    }

    private Long add(Fixture fixture, String status, OffsetDateTime reservedAt, Long copyId) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, fixture.bookId(), fixture.readerId(), copyId, status, reservedAt,
                copyId == null ? null : reservedAt.plusDays(3));
    }

    @Test void readyCancellationTransfersHeldCopyToFirstWaiterAndRecalculatesQueue() {
        Fixture owner = fixture();
        Fixture anotherReader = fixture();
        Fixture nextReader = new Fixture(owner.bookId(), anotherReader.readerId());
        OffsetDateTime base = OffsetDateTime.now().minusDays(10);
        Long copyId = heldCopy(owner);
        Long targetId = add(owner, "READY_FOR_PICKUP", base, copyId);
        Long firstWaitingId = add(nextReader, "PENDING", base.plusHours(1), null);
        Long secondWaitingId = add(owner, "PENDING", base.plusHours(2), null);

        service.cancelMine(targetId, owner.readerId());
        entityManager.flush();
        entityManager.clear();

        assertThat(reservations.findById(targetId).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        var promoted = reservations.findById(firstWaitingId).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(promoted.getBookCopy()).isNotNull();
        assertThat(promoted.getBookCopy().getId()).isEqualTo(copyId);
        assertThat(copies.findById(copyId).orElseThrow().getStatus()).isEqualTo("HELD");
        assertThat(promoted.getPickupDeadline()).isAfter(OffsetDateTime.now())
                .isAfter(base.plusDays(3));
        assertThat(reservations.findPendingQueuePosition(secondWaitingId)).isEqualTo(1L);
        assertThat(reservations.countPendingQueueByBookId(owner.bookId())).isEqualTo(1L);
    }

    @Test void readyCancellationWithoutWaiterReturnsCopyToAvailableAndUpdatesAvailableCount() {
        Fixture owner = fixture();
        OffsetDateTime base = OffsetDateTime.now().minusDays(1);
        Long copyId = heldCopy(owner);
        Long targetId = add(owner, "READY_FOR_PICKUP", base, copyId);
        assertThat(copies.countAvailableByBookId(owner.bookId())).isZero();

        service.cancelMine(targetId, owner.readerId());
        entityManager.flush();
        entityManager.clear();

        assertThat(reservations.findById(targetId).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(copies.findById(copyId).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
        assertThat(copies.countAvailableByBookId(owner.bookId())).isEqualTo(1L);
    }
}
