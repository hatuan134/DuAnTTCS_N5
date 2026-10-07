package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.ReservationLoanContextResponse;
import com.duanttcsn5.library.dto.loan.ReservationLoanResponse;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/** Lending is a new domain; reservation creation/cancellation stays in its existing service. */
@Service
public class LoanService {
    private final BookRepository books;
    private final BookReservationRepository reservations;
    private final BookCopyRepository copies;
    private final LibraryCardRepository cards;
    private final UserRepository users;
    private final LoanRepository loans;

    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans) {
        this.books = books;
        this.reservations = reservations;
        this.copies = copies;
        this.cards = cards;
        this.users = users;
        this.loans = loans;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReservationLoanContextResponse pickupContext(Long reservationId) {
        validateId(reservationId);
        BookReservation reservation = reservations.findReadyForPickupById(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "READY_RESERVATION_NOT_FOUND",
                        "Không tìm thấy đơn đặt giữ đang chờ nhận. Đơn có thể đã đổi trạng thái."));
        String cardNumber = cards.findByUserIdWithDetails(reservation.getReader().getId())
                .map(card -> card.getCardNumber()).orElse(null);
        String loanNumber = loans.findNumberByReservation(reservationId).orElse(null);
        boolean converted = loanNumber != null || reservations.hasLoanLinkedToReservation(reservationId);
        return new ReservationLoanContextResponse(cardNumber, converted, loanNumber);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationLoanResponse createFromReservation(Long reservationId, Long actorId, String cardNumber) {
        if (actorId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED",
                "Vui lòng đăng nhập để lập phiếu mượn.");
        var actor = users.findById(actorId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (actor.getRole() == null || actor.getRole().getCode() == null
                || !Set.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN").contains(actor.getRole().getCode())
                || !"ACTIVE".equals(actor.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAFF_ROLE_REQUIRED",
                    "Chỉ nhân viên thư viện đang hoạt động mới được lập phiếu mượn.");
        }
        validateId(reservationId);
        String confirmed = cardNumber == null ? "" : cardNumber.strip();
        if (confirmed.isBlank() || confirmed.length() > 100
                || confirmed.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER",
                    "Vui lòng nhập mã thẻ hợp lệ, tối đa 100 ký tự.");
        }

        // Same title -> reservation -> copy lock order as reservation cancellation.
        Long bookId = reservations.findBookIdForCancellation(reservationId).orElseThrow(this::notFound);
        books.findForReservation(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        BookReservation reservation = reservations.findForCancellation(reservationId).orElseThrow(this::notFound);
        if (!bookId.equals(reservation.getBook().getId())) throw new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_CHANGED", "Đơn đã thay đổi. Vui lòng tải lại.");
        if ("FULFILLED".equals(reservation.getStatus())
                || loans.findNumberByReservation(reservationId).isPresent()
                || reservations.hasLoanLinkedToReservation(reservationId)) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_ALREADY_CONVERTED",
                    "Đơn đặt giữ này đã chuyển thành phiếu mượn. Không thể lập thêm phiếu.");
        }
        if (!"READY_FOR_PICKUP".equals(reservation.getStatus())) throw new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_NOT_READY", "Chỉ được lập phiếu từ đơn đặt giữ đang Chờ nhận.");

        Long readerId = reservation.getReader().getId();
        var card = cards.findByUserIdWithDetails(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "LIBRARY_CARD_REQUIRED",
                        "Bạn đọc của đơn đặt giữ chưa có thẻ thư viện để đối chiếu."));
        if (!confirmed.equals(card.getCardNumber())) throw new ApiException(HttpStatus.BAD_REQUEST,
                "RESERVATION_CARD_MISMATCH", "Mã thẻ không đúng với bạn đọc sở hữu đơn đặt giữ.");
        if (reservation.getBookCopy() == null) throw invalidCopy();
        var copy = copies.findForStatusChange(reservation.getBookCopy().getId()).orElseThrow(this::invalidCopy);
        if (!bookId.equals(copy.getBook().getId()) || !"HELD".equals(copy.getStatus())
                || copies.hasUnreturnedLoan(copy.getId())) throw invalidCopy();

        OffsetDateTime borrowedAt = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .truncatedTo(ChronoUnit.MICROS);
        String loanNumber = "PM-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        Long loanId = loans.insert(reservationId, readerId, actorId, loanNumber, borrowedAt);
        loans.insertItem(loanId, copy.getId(), borrowedAt);
        // The existing database trigger owns the copy transition to BORROWED.
        // Reservation status/deadline lifecycle is explicitly deferred to later slices.
        return new ReservationLoanResponse(loanId, loanNumber, reservationId,
                readerId, reservation.getReader().getFullName(), card.getCardNumber(),
                bookId, reservation.getBook().getTitle(), copy.getId(), copy.getBarcode(),
                borrowedAt, "Đã lập phiếu mượn thành công từ đơn đặt giữ.");
    }

    private void validateId(Long id) {
        if (id == null || id < 1) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_RESERVATION_ID", "Mã đơn đặt giữ không hợp lệ.");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ.");
    }

    private ApiException invalidCopy() {
        return new ApiException(HttpStatus.CONFLICT, "RESERVATION_COPY_CONFLICT",
                "Đơn không có bản sao đang giữ hợp lệ. Vui lòng tải lại và đối chiếu dữ liệu.");
    }
}
