package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.BookReservationQueueResponse;
import com.duanttcsn5.library.entity.BookReservation;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the actual JPA query/positions on a dedicated PostgreSQL 15 test database.
 * Opt in with S2_09_2_DB_TEST=true using the project's existing DB/JWT environment.
 * Fixture rows roll back; migrations persist and PostgreSQL sequences may advance.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_09_2_DB_TEST", matches = "true")
@Transactional
class BookReservationQueueRepositoryTest {
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
    void emptyTitleNeverShowsAnotherTitlesOrders() {
        Fixture empty = fixture(), other = fixture();
        add(other, "PENDING", "2030-01-01T08:00:00+07:00", null);
        assertThat(repository.findAllForQueueByBookId(empty.bookId())).isEmpty();
        var response = service.getQueueByBookId(empty.bookId());
        assertThat(response.bookId()).isEqualTo(empty.bookId());
        assertThat(response.items()).isEmpty();
    }

    @Test
    void onePendingOrderHasCurrentPositionOne() {
        Fixture f = fixture();
        Long id = add(f, "PENDING", "2030-01-01T08:00:00+07:00", null);
        var result = service.getQueueByBookId(f.bookId()).items();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).queuePosition()).isEqualTo(1L);
        assertThat(result.get(0).readerName()).isEqualTo(f.readerName());
        assertThat(result.get(0).reservedAt().toInstant())
                .isEqualTo(OffsetDateTime.parse("2030-01-01T08:00:00+07:00").toInstant());
    }

    @Test
    void historySortsByTimeAndIdWithoutOtherTitlesAffectingPositions() {
        Fixture f = fixture(), other = fixture();
        String barcode = "S2092-" + UUID.randomUUID();
        Long cancelled = add(f, "CANCELLED", "2030-01-01T08:00:00+07:00", null);
        Long ready = add(f, "READY_FOR_PICKUP", "2030-01-01T09:00:00+07:00", barcode);
        Long fulfilled = add(f, "FULFILLED", "2030-01-02T08:00:00+07:00", null);
        Long expired = add(f, "EXPIRED", "2030-01-02T09:00:00+07:00", null);
        Long late = add(f, "PENDING", "2030-01-03T10:00:00+07:00", null);
        // Inserted later, but timestamp is earlier. 02:00Z = 09:00 Vietnam.
        Long early = add(f, "PENDING", "2030-01-03T02:00:00Z", null);
        Long tie = add(f, "PENDING", "2030-01-03T10:00:00+07:00", null);
        Long unrelated = add(other, "PENDING", "2029-12-01T08:00:00+07:00", null);

        var rows = repository.findAllForQueueByBookId(f.bookId());
        assertThat(rows).extracting(BookReservation::getId)
                .containsExactly(cancelled, ready, fulfilled, expired, early, late, tie).doesNotContain(unrelated);
        assertThat(rows).allMatch(r -> r.getBook().getId().equals(f.bookId()));
        var queue = service.getQueueByBookId(f.bookId()).items();
        assertThat(queue).extracting(BookReservationQueueResponse.QueueEntry::queuePosition)
                .containsExactly(null, null, null, null, 1L, 2L, 3L);
        assertThat(queue.get(1).barcode()).isEqualTo(barcode);
        for (var item : queue) {
            if (item.queuePosition() != null) {
                assertThat(item.queuePosition().longValue()).isEqualTo(repository.findPendingQueuePosition(item.id()));
            }
        }
        jdbc.update("UPDATE book_reservations SET status = 'CANCELLED' WHERE id = ?", early);
        entityManager.clear(); // JDBC changed data outside Hibernate's entity cache.
        var refreshed = service.getQueueByBookId(f.bookId()).items();
        assertThat(refreshed).extracting(BookReservationQueueResponse.QueueEntry::id)
                .containsExactly(cancelled, ready, fulfilled, expired, early, late, tie);
        assertThat(refreshed).extracting(BookReservationQueueResponse.QueueEntry::queuePosition)
                .containsExactly(null, null, null, null, null, 1L, 2L);
        assertThat(repository.findAllForQueueByBookId(other.bookId())).extracting(BookReservation::getId)
                .containsExactly(unrelated);
    }
}
