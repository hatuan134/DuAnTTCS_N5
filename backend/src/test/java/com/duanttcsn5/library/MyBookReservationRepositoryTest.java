package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.MyBookReservationResponse;
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
 * Tests the actual JPA query/positions on a dedicated PostgreSQL 15 test database.
 * Opt in with S2_08_1_DB_TEST=true using the project's existing DB/JWT environment.
 * Fixture rows roll back; migrations persist and PostgreSQL sequences may advance.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_08_1_DB_TEST", matches = "true")
@Transactional
class MyBookReservationRepositoryTest {
    @Autowired private BookReservationRepository repository;
    @Autowired private BookReservationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private record Fixture(Long bookId, Long readerId, String readerName) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String name = "Bạn đọc " + suffix;
        Long readerId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, name, suffix + "@example.invalid");
        Long bookId = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "Sách kiểm thử " + suffix);
        return new Fixture(bookId, readerId, name);
    }

    private Long add(Fixture fixture, String status, String time, String barcode) {
        var created = OffsetDateTime.parse(time);
        Long copyId = null;
        if (barcode != null) {
            copyId = jdbc.queryForObject("""
                    INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                        received_date, cover_price, physical_condition)
                    VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD')
                    RETURNING id
                    """, Long.class, fixture.bookId(), barcode);
        }
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, fixture.bookId(), fixture.readerId(), copyId, status,
                created, copyId == null ? null : created.plusDays(3));
    }

    @Test
    void noOrdersReturnsEmptyAndOnePendingStartsAtOne() {
        Fixture f = fixture(), other = fixture();
        add(other, "PENDING", "2030-01-01T08:00:00+07:00", null);
        assertThat(service.getMyReservations(f.readerId())).isEmpty();
        Long id = add(f, "PENDING", "2030-01-01T08:00:00+07:00", null);
        var result = service.getMyReservations(f.readerId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).queuePosition()).isEqualTo(1L);
    }

    @Test
    void countsOtherReadersInSameTitleButReturnsOnlyOwnOrdersAndRefreshesPosition() {
        Fixture own = fixture(), other = fixture();
        Fixture aheadReader = new Fixture(own.bookId(), other.readerId(), other.readerName());
        Long ahead = add(aheadReader, "PENDING", "2030-01-01T08:00:00+07:00", null);
        Long first = add(own, "PENDING", "2030-01-01T01:00:00Z", null);
        Long second = add(own, "PENDING", "2030-01-01T08:00:00+07:00", null);
        add(other, "PENDING", "2029-12-01T08:00:00+07:00", null);
        add(aheadReader, "CANCELLED", "2029-12-01T08:00:00+07:00", null);
        var rows = service.getMyReservations(own.readerId());
        assertThat(rows).extracting(MyBookReservationResponse::id).containsExactly(second, first);
        assertThat(rows).extracting(MyBookReservationResponse::queuePosition).containsExactly(3L, 2L);
        assertThat(rows).allMatch(r -> r.pickupDeadline() == null && r.bookId().equals(own.bookId()));
        jdbc.update("UPDATE book_reservations SET status = 'CANCELLED' WHERE id = ?", ahead);
        entityManager.clear();
        assertThat(service.getMyReservations(own.readerId()))
                .extracting(MyBookReservationResponse::queuePosition).containsExactly(2L, 1L);
    }

    @Test
    void readyFirstByDeadlineThenPendingThenHistoryAndUnallocatedDeadlineHidden() {
        Fixture f = fixture();
        Long cancelled = add(f, "CANCELLED", "2030-01-09T08:00:00+07:00", null);
        Long pending = add(f, "PENDING", "2030-01-08T08:00:00+07:00", null);
        Long later = add(f, "READY_FOR_PICKUP", "2030-01-03T08:00:00+07:00", "S2081-" + UUID.randomUUID());
        Long earlier = add(f, "READY_FOR_PICKUP", "2030-01-02T08:00:00+07:00", "S2081-" + UUID.randomUUID());
        Long legacy = add(f, "READY_FOR_PICKUP", "2030-01-01T08:00:00+07:00", null);
        Long fulfilled = add(f, "FULFILLED", "2030-01-07T08:00:00+07:00", null);
        Long expired = add(f, "EXPIRED", "2030-01-06T08:00:00+07:00", null);
        var rows = service.getMyReservations(f.readerId());
        assertThat(rows).extracting(MyBookReservationResponse::id)
                .containsExactly(earlier, later, legacy, pending, cancelled, fulfilled, expired);
        assertThat(rows.get(0).pickupDeadline().toInstant())
                .isEqualTo(OffsetDateTime.parse("2030-01-05T08:00:00+07:00").toInstant());
        assertThat(rows.get(1).pickupDeadline()).isNotNull();
        assertThat(rows.subList(2, 7)).allMatch(r -> r.pickupDeadline() == null);
        assertThat(rows).extracting(MyBookReservationResponse::queuePosition)
                .containsExactly(null, null, null, 1L, null, null, null);
        // Legacy data can contain a deadline without an allocated copy.
        jdbc.update("UPDATE book_reservations SET pickup_deadline = ? WHERE id = ?",
                OffsetDateTime.parse("2030-01-10T17:15:30+07:00"), legacy);
        entityManager.clear();
        assertThat(service.getMyReservations(f.readerId()).stream().filter(r -> r.id().equals(legacy)).findFirst().orElseThrow()
                .pickupDeadline()).isNull();
    }
}
