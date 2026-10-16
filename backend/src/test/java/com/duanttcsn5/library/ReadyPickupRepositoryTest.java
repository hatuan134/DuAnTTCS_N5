package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.repository.BookReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in PostgreSQL test of the ACTUAL repository queries and current migrations.
 * Use a dedicated test database and the project's existing DB/JWT environment.
 * Test fixture rows roll back; PostgreSQL sequences may advance.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_09_1_DB_TEST", matches = "true")
@Transactional
class ReadyPickupRepositoryTest {
    @Autowired private BookReservationRepository repository;
    @Autowired private JdbcTemplate jdbc;

    private record Fixture(Long bookId, Long readerId, String suffix) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long readerId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Bạn đọc " + suffix, suffix + "@example.invalid");
        Long bookId = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "Sách kiểm thử " + suffix);
        return new Fixture(bookId, readerId, suffix);
    }

    private Long add(Fixture fixture, String status, String deadline, boolean allocated) {
        Long copyId = null;
        if (allocated) {
            copyId = jdbc.queryForObject("""
                    INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                        received_date, cover_price, physical_condition)
                    VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD')
                    RETURNING id
                    """, Long.class, fixture.bookId(), "S209-" + UUID.randomUUID());
        }
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, fixture.bookId(), fixture.readerId(), copyId, status,
                OffsetDateTime.parse("2030-01-01T08:00:00+07:00"),
                deadline == null ? null : OffsetDateTime.parse(deadline));
    }

    @Test
    void nearestDeadlineFirstWithStableTiesAndUnknownDeadlineLast() {
        Fixture f = fixture();
        Long late = add(f, "READY_FOR_PICKUP", "2030-01-08T17:00:00+07:00", true);
        Long early = add(f, "READY_FOR_PICKUP", "2030-01-05T09:00:00+07:00", true);
        Long tie = add(f, "READY_FOR_PICKUP", "2030-01-05T09:00:00+07:00", true);
        Long unknown = add(f, "READY_FOR_PICKUP", null, false);
        // 04:00 UTC = 11:00 Vietnam, after the 09:00 pickup deadlines.
        Long offset = add(f, "READY_FOR_PICKUP", "2030-01-05T04:00:00Z", true);
        List<Long> ids = List.of(late, early, tie, unknown, offset);
        var rows = repository.findReadyForPickup().stream().filter(r -> ids.contains(r.getId())).toList();
        assertThat(rows).extracting(BookReservation::getId).containsExactly(early, tie, offset, late, unknown);
        assertThat(rows).allMatch(r -> "READY_FOR_PICKUP".equals(r.getStatus()));
        var selected = repository.findReadyForPickupById(early).orElseThrow();
        String storedBarcode = jdbc.queryForObject("SELECT barcode FROM book_copies WHERE id = ?",
                String.class, selected.getBookCopy().getId());
        assertThat(selected.getBookCopy().getBarcode()).isEqualTo(storedBarcode);
        assertThat(selected.getBook().getTitle()).isEqualTo("Sách kiểm thử " + f.suffix());
        assertThat(selected.getReader().getFullName()).isEqualTo("Bạn đọc " + f.suffix());
        assertThat(selected.getPickupDeadline().toInstant())
                .isEqualTo(OffsetDateTime.parse("2030-01-05T09:00:00+07:00").toInstant());
    }

    @Test
    void everyOtherStatusIsExcludedAndChangedOrderCannotBeOpened() {
        Fixture f = fixture();
        var excluded = new ArrayList<Long>();
        for (String status : new String[]{"PENDING", "FULFILLED", "CANCELLED", "EXPIRED"}) {
            Long id = add(f, status, null, false);
            excluded.add(id);
            assertThat(repository.findReadyForPickupById(id)).isEmpty();
        }
        Long ready = add(f, "READY_FOR_PICKUP", "2030-01-06T17:00:00+07:00", true);
        assertThat(repository.findReadyForPickup()).extracting(BookReservation::getId)
                .contains(ready).doesNotContainAnyElementsOf(excluded);
        jdbc.update("UPDATE book_reservations SET status = 'FULFILLED' WHERE id = ?", ready);
        assertThat(repository.findReadyForPickupById(ready)).isEmpty();
        assertThat(repository.findReadyForPickup()).extracting(BookReservation::getId).doesNotContain(ready);
    }
}
