package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Opt in on a disposable migrated PostgreSQL database; fixture writes roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_02_4_DB_TEST", matches = "true")
@Transactional
class DirectLoanReservationDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookReservationRepository reservations;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;
    private Long reader, other, staff, book;
    private String card;
    private OffsetDateTime checkedAt;

    @BeforeEach void setup() {
        checkedAt = OffsetDateTime.now().withNano(0);
        reader = user("READER", "S3024 Nguyễn Văn An");
        other = user("READER", "S3024 Trần Thị Bình");
        staff = user("LIBRARIAN", "S3024 Thủ thư");
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 5, 14, 1, 7) RETURNING id
                """, Long.class, "S3024-" + UUID.randomUUID());
        card = "S3024-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 30, 'ACTIVE')
                """, card, reader, type);
        book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3024 " + UUID.randomUUID());
    }

    private Long user(String role, String name) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, name, UUID.randomUUID() + "@example.invalid", role);
    }

    private Long copy(String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), ?, CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3024-" + UUID.randomUUID(), status);
    }

    private Long reservation(Long copy, Long owner, String status, OffsetDateTime deadline) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, book, owner, copy, status, checkedAt.minusDays(2), deadline);
    }

    private AddDirectLoanItemRequest request(Long copy) {
        String barcode = jdbc.queryForObject("SELECT barcode FROM book_copies WHERE id = ?", String.class, copy);
        return new AddDirectLoanItemRequest(card, barcode, List.of());
    }

    @Test void queryUsesExactCopyActiveStatusAndInclusiveMicrosecondDeadline() {
        Long target = copy("HELD"), different = copy("HELD");
        Long id = reservation(target, other, "READY_FOR_PICKUP", checkedAt);
        reservation(different, reader, "READY_FOR_PICKUP", checkedAt.plusDays(1));
        reservation(target, reader, "CANCELLED", checkedAt.plusDays(1));
        var result = reservations.findEffectiveHoldForCopy(target, checkedAt).orElseThrow();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getReader().getId()).isEqualTo(other);
        assertThat(result.getReader().getFullName()).isEqualTo("S3024 Trần Thị Bình");
        assertThat(reservations.findEffectiveHoldForCopy(target, checkedAt.plusNanos(1000))).isEmpty();
    }

    @Test void historicalAndExpiredOrdersAndUnallocatedPendingTitleDoNotBlockAvailableCopies() {
        Long expired = copy("AVAILABLE"), cancelled = copy("AVAILABLE"), fulfilled = copy("AVAILABLE");
        Long free = copy("AVAILABLE");
        reservation(expired, other, "READY_FOR_PICKUP", checkedAt.minusSeconds(1));
        reservation(cancelled, other, "CANCELLED", checkedAt.plusDays(1));
        reservation(fulfilled, other, "FULFILLED", checkedAt.plusDays(1));
        reservation(null, other, "PENDING", null);
        for (Long id : List.of(expired, cancelled, fulfilled, free)) {
            assertThat(reservations.findEffectiveHoldForCopy(id, checkedAt)).isEmpty();
            assertThat(service.previewDirectLoanItem(request(id), staff).bookCopyId()).isEqualTo(id);
        }
    }

    @Test void realHoldReturnsTheCorrectOwnerAndOrderAndPreviewDoesNotWriteAnyLendingData() {
        Long target = copy("HELD"), first = copy("AVAILABLE"), last = copy("AVAILABLE");
        reservation(target, reader, "CANCELLED", checkedAt.plusDays(1));
        Long order = reservation(target, other, "READY_FOR_PICKUP", checkedAt.plusDays(1));
        var beforeLoans = jdbc.queryForList("SELECT * FROM loans ORDER BY id");
        var beforeItems = jdbc.queryForList("SELECT * FROM loan_items ORDER BY id");
        var beforeCopies = jdbc.queryForList("SELECT * FROM book_copies ORDER BY id");
        var beforeOrders = jdbc.queryForList("SELECT * FROM book_reservations ORDER BY id");
        assertThat(service.previewDirectLoanItem(request(first), staff).bookCopyId()).isEqualTo(first);
        assertThatThrownBy(() -> service.previewDirectLoanItem(request(target), staff))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER");
                    assertThat(e.getDetails()).containsEntry("reservationId", order)
                            .containsEntry("readerName", "S3024 Trần Thị Bình");
                });
        assertThat(service.previewDirectLoanItem(request(last), staff).bookCopyId()).isEqualTo(last);
        assertThat(jdbc.queryForList("SELECT * FROM loans ORDER BY id")).isEqualTo(beforeLoans);
        assertThat(jdbc.queryForList("SELECT * FROM loan_items ORDER BY id")).isEqualTo(beforeItems);
        assertThat(jdbc.queryForList("SELECT * FROM book_copies ORDER BY id")).isEqualTo(beforeCopies);
        assertThat(jdbc.queryForList("SELECT * FROM book_reservations ORDER BY id")).isEqualTo(beforeOrders);
    }

    @Test void ownHoldRequiresReservationFlowAndConvertedOrderUsesBorrowedStatusInstead() {
        Long target = copy("HELD");
        Long order = reservation(target, reader, "READY_FOR_PICKUP", checkedAt.plusDays(1));
        assertThatThrownBy(() -> service.previewDirectLoanItem(request(target), staff))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_HELD_FOR_CURRENT_READER"));
        Long loan = loans.insert(order, reader, staff, "S3024-" + UUID.randomUUID(), checkedAt);
        loans.insertItem(loan, target, checkedAt, checkedAt.plusDays(14));
        // JDBC trigger changed the copy; discard the JPA snapshot from the first preview.
        entityManager.clear();
        assertThatThrownBy(() -> service.previewDirectLoanItem(request(target), staff))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_NOT_AVAILABLE");
                    assertThat(e.getMessage()).contains("Đang mượn").doesNotContain("đơn đặt giữ #");
                });
    }

    @Autowired jakarta.persistence.EntityManager entityManager;
}
