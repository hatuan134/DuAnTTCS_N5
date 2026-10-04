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
 * Opt in with S2_08_2_DB_TEST=true using the project's existing DB/JWT environment.
 * Fixture rows roll back; migrations persist and PostgreSQL sequences may advance.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_08_2_DB_TEST", matches = "true")
@Transactional
class ReaderReservationCancellationDatabaseTest {
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2})
    void cancellingHeadMiddleOrTailRecalculatesPositionsAndPersistsHistory(int index) {
        Fixture own = fixture(), other = fixture();
        Fixture sameBookOtherReader = new Fixture(own.bookId(), other.readerId(), other.readerName());
        Long[] ids = {
            add(own, "PENDING", "2030-01-01T08:00:00+07:00", null),
            add(sameBookOtherReader, "PENDING", "2030-01-01T08:00:00+07:00", null),
            add(own, "PENDING", "2030-01-01T08:00:00+07:00", null)
        };
        Long untouched = add(other, "PENDING", "2030-01-01T08:00:00+07:00", null);
        service.cancelMine(ids[index], index == 1 ? other.readerId() : own.readerId());
        entityManager.flush(); entityManager.clear();
        assertThat(repository.findById(ids[index]).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        long position = 0;
        for (int i = 0; i < ids.length; i++) {
            if (i != index) assertThat(repository.findPendingQueuePosition(ids[i])).isEqualTo(++position);
        }
        assertThat(repository.countPendingQueueByBookId(own.bookId())).isEqualTo(2);
        assertThat(repository.findPendingQueuePosition(untouched)).isEqualTo(1);
        var history = service.getMyReservations(index == 1 ? other.readerId() : own.readerId());
        var cancelled = history.stream().filter(row -> row.id().equals(ids[index])).findFirst().orElseThrow();
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.queuePosition()).isNull();
        assertThat(cancelled.pickupDeadline()).isNull();
    }
}
