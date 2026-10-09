package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.ConfirmReturnRequest;
import com.duanttcsn5.library.dto.loan.ConfirmReturnResponse;

import com.duanttcsn5.library.dto.loan.ReturnLookupResponse;
import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse;
import com.duanttcsn5.library.dto.loan.LoanRejectionResponse;
import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse.BlockReason;
import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.dto.loan.DirectLoanItemResponse;
import com.duanttcsn5.library.dto.loan.CreateDirectLoanRequest;
import com.duanttcsn5.library.dto.loan.DirectLoanResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.HashSet;
import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.MyBorrowedBookResponse;
import com.duanttcsn5.library.dto.loan.RenewalCheckResponse;
import com.duanttcsn5.library.dto.loan.MyReturnedBooksPageResponse;
import com.duanttcsn5.library.dto.loan.OverdueLoanItemResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
import com.duanttcsn5.library.dto.loan.LoanSearchResultResponse;
import com.duanttcsn5.library.dto.loan.LoanSearchPageResponse;
import com.duanttcsn5.library.dto.loan.ReservationLoanContextResponse;
import com.duanttcsn5.library.dto.loan.ReservationLoanResponse;
import com.duanttcsn5.library.dto.loan.LoanDatePreviewResponse;
import com.duanttcsn5.library.dto.book.ReadyForPickupReservationResponse;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.exception.ReservationPickupExpiredException;
import org.springframework.beans.factory.annotation.Autowired;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/** Lending is a new domain; reservation creation/cancellation stays in its existing service. */
@Service
public class LoanService {
    private final BookRepository books;
    private final BookReservationRepository reservations;
    private final BookCopyRepository copies;
    private final LibraryCardRepository cards;
    private final UserRepository users;
    private final LoanRepository loans;
    private final LibraryConfigurationService configuration;
    private final Clock clock;
    private final LoanRejectionLogService rejectionLogs;
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Provisional policy pending PO approval: only borrowing/fee/overdue policy blocks.
    // Identity, account/card validity, configuration and physical-copy constraints NEVER bypass.
    private static final Set<String> OVERRIDABLE = Set.of(
            "LOAN_LIMIT_REACHED", "LOAN_DRAFT_LIMIT_EXCEEDED",
            "LOAN_OVERDUE_UNRETURNED", "LOAN_UNPAID_FEES");
    private static final String MISSING_DEADLINE =
            "Đơn chưa có hạn nhận hợp lệ. Vui lòng đối chiếu dữ liệu trước khi lập phiếu mượn.";

    @Autowired
    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans, LibraryConfigurationService configuration,
                       LoanRejectionLogService rejectionLogs) {
        this(books, reservations, copies, cards, users, loans, configuration, Clock.system(LIBRARY_ZONE), rejectionLogs);
    }

    /** Existing constructors are kept for backward-compatible unit tests. */
    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans, LibraryConfigurationService configuration) {
        this(books, reservations, copies, cards, users, loans, configuration, Clock.system(LIBRARY_ZONE), null);
    }

    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans, LibraryConfigurationService configuration, Clock clock) {
        this(books, reservations, copies, cards, users, loans, configuration, clock, null);
    }

    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans, LibraryConfigurationService configuration,
                       Clock clock, LoanRejectionLogService rejectionLogs) {
        this.books = books;
        this.reservations = reservations;
        this.copies = copies;
        this.cards = cards;
        this.users = users;
        this.loans = loans;
        this.configuration = configuration;
        this.clock = clock;
        this.rejectionLogs = rejectionLogs;
    }

    /** S3-07.1. PO policy: calendar days in Vietnam, including closed dates.
     * Keep the stored deadline; creation/renewal already owns deadline adjustment.
     * Today equal to the deadline is on time, even after its stored closing hour.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReturnLookupResponse lookupReturn(String barcode, Long actorId) {
        requireStaff(actorId);
        String normalized = barcode == null ? "" : barcode.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BARCODE",
                    "Vui lòng nhập mã vạch từ 1 đến 100 ký tự.");
        }
        var row = loans.findReturnLookup(normalized).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COPY_BARCODE_NOT_FOUND",
                        "Mã vạch này không tồn tại trong thư viện."));
        LocalDate today = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE);
        String status;
        String message;
        Long overdueDays = null;
        if (row.itemId() == null) {
            status = "NOT_BORROWED";
            message = "Bản sao này hiện không có ai mượn.";
        } else if (row.dueAt() == null) {
            status = "MISSING_DUE_DATE";
            message = "Phiếu mượn chưa có hạn trả; chưa thể xác định số ngày trễ.";
        } else {
            LocalDate dueDate = row.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate();
            overdueDays = Math.max(0L, ChronoUnit.DAYS.between(dueDate, today));
            status = overdueDays > 0 ? "OVERDUE" : "ON_TIME";
            message = overdueDays > 0 ? "Sách quá hạn " + overdueDays + " ngày." : "Sách đang trong hạn trả.";
        }
        return new ReturnLookupResponse(status, message, row.copyId(), row.barcode(), row.bookTitle(),
                row.loanId(), row.loanNumber(), row.itemId(), row.readerId(), row.readerName(),
                row.borrowedAt(), row.dueAt(), today, overdueDays);
    }

    /** S3-07.3: return one copy and allocate it to the first eligible FIFO waiter atomically. */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ConfirmReturnResponse confirmReturn(ConfirmReturnRequest request, Long actorId) {
        requireStaff(actorId, "nhận trả sách");
        String barcode = request == null || request.barcode() == null ? "" : request.barcode().trim();
        if (barcode.isEmpty() || barcode.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BARCODE",
                    "Vui lòng nhập mã vạch từ 1 đến 100 ký tự.");
        }
        if (request.itemId() == null || request.itemId() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_ITEM_ID",
                    "Vui lòng tra cứu phiếu mượn hợp lệ trước khi xác nhận.");
        }
        try {
            Long bookId = loans.lockTitleForReturn(request.itemId(), barcode).orElseThrow(() ->
                    new ApiException(HttpStatus.CONFLICT, "RETURN_PREVIEW_CHANGED",
                            "Phiếu mượn không còn khớp mã vạch. Vui lòng tra cứu lại trước khi nhận trả."));
            var candidate = loans.lockReturnCandidate(request.itemId(), barcode).orElseThrow(() ->
                    new ApiException(HttpStatus.CONFLICT, "RETURN_PREVIEW_CHANGED",
                            "Phiếu mượn không còn khớp mã vạch. Vui lòng tra cứu lại trước khi nhận trả."));
            if (candidate.returnedAt() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "LOAN_ALREADY_RETURNED",
                        "Cuốn sách trong phiếu này đã được nhận trả. Không thể xác nhận lại.");
            }
            // Title -> loan/item -> pending reservations -> copy. Creation/cancellation
            // share the title lock, so concurrent returns cannot reorder or share a waiter.
            loans.lockPendingQueueForReturn(bookId);
            String copyStatus = loans.lockCopyForReturn(candidate.copyId()).orElseThrow(() ->
                    new ApiException(HttpStatus.CONFLICT, "RETURN_COPY_NOT_FOUND",
                            "Không tìm thấy bản sao cần nhận trả. Vui lòng tra cứu lại."));
            if (!"BORROWED".equals(copyStatus)) {
                throw new ApiException(HttpStatus.CONFLICT, "RETURN_COPY_STATUS_MISMATCH",
                        "Trạng thái bản sao không khớp phiếu đang mượn. Dữ liệu được giữ nguyên.");
            }
            OffsetDateTime returnedAt = OffsetDateTime.ofInstant(clock.instant(), LIBRARY_ZONE)
                    .truncatedTo(ChronoUnit.MICROS);
            if (returnedAt.isBefore(candidate.borrowedAt())) {
                throw new ApiException(HttpStatus.CONFLICT, "RETURN_BEFORE_BORROWED_AT",
                        "Ngày giờ hệ thống đang trước ngày mượn. Chưa thể ghi nhận trả sách.");
            }
            var next = loans.findEligiblePendingForReturn(bookId, returnedAt.toLocalDate()).orElse(null);
            if (next != null) {
                OffsetDateTime deadline = configuration.calculateReservationPickupDeadline(returnedAt);
                if (next.reservedAt() == null || next.reservedAt().isAfter(returnedAt)
                        || deadline == null || !deadline.isAfter(returnedAt)) {
                    throw new ApiException(HttpStatus.CONFLICT, "INVALID_PICKUP_DEADLINE",
                            "Không xác định được thời điểm giữ và hạn nhận hợp lệ. Dữ liệu nhận trả được giữ nguyên.");
                }
                if (loans.allocateReturnedCopy(next.id(), bookId, candidate.copyId(), returnedAt, deadline) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "RETURN_QUEUE_CHANGED",
                            "Đơn đầu hàng đợi đã thay đổi. Dữ liệu nhận trả được giữ nguyên, vui lòng tra cứu lại.");
                }
            }
            // V33 chooses HELD when the queue allocation above exists, otherwise AVAILABLE.
            // There is no intermediate AVAILABLE update for a copy assigned to a waiter.
            if (loans.markReturned(candidate.itemId(), candidate.copyId(), returnedAt, actorId) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "RETURN_UPDATE_FAILED",
                        "Phiếu hoặc tài khoản thao tác đã thay đổi. Dữ liệu được giữ nguyên, vui lòng tra cứu lại.");
            }
            var result = loans.findReturnConfirmation(candidate.itemId()).orElseThrow(() ->
                    new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RETURN_SAVE_FAILED",
                            "Không thể ghi nhận đầy đủ kết quả nhận trả. Dữ liệu được giữ nguyên."));
            boolean valid = next == null
                    ? "AVAILABLE".equals(result.copyStatus()) && result.nextReservationId() == null
                    : "HELD".equals(result.copyStatus()) && next.id().equals(result.nextReservationId())
                        && result.holdStartedAt() != null && returnedAt.isEqual(result.holdStartedAt())
                        && result.pickupDeadline() != null;
            if (!valid) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RETURN_SAVE_FAILED",
                        "Không thể lưu đúng trạng thái bản sao và đơn đặt giữ. Dữ liệu được giữ nguyên.");
            }
            return result;
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RETURN_SAVE_FAILED",
                    "Không thể nhận trả và chuyển hàng đợi. Dữ liệu được giữ nguyên, vui lòng thử lại.");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReservationLoanContextResponse pickupContext(Long reservationId) {
        validateId(reservationId);
        BookReservation reservation = reservations.findForLoanContext(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "READY_RESERVATION_NOT_FOUND",
                        "Không tìm thấy đơn đặt giữ đang chờ nhận. Đơn có thể đã đổi trạng thái."));
        return context(reservation, now());
    }

    /** Explicit write check used when staff opens/refreshes the confirmation view. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationLoanContextResponse checkPickup(Long reservationId, Long actorId) {
        requireStaff(actorId);
        validateId(reservationId);
        Long bookId = reservations.findBookIdForCancellation(reservationId).orElseThrow(this::notFound);
        books.findForReservation(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        BookReservation reservation = reservations.findForCancellation(reservationId).orElseThrow(this::notFound);
        if (!bookId.equals(reservation.getBook().getId())) throw new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_CHANGED", "Đơn đã thay đổi. Vui lòng tải lại.");
        if (!Set.of("READY_FOR_PICKUP", "FULFILLED", "EXPIRED").contains(reservation.getStatus())) throw notFound();
        if ("READY_FOR_PICKUP".equals(reservation.getStatus()) && !converted(reservation)
                && reservation.getPickupDeadline() != null) {
            // Sample time after waiting for all relevant locks, not when the request arrived.
            BookCopy copy = reservation.getBookCopy() == null ? null : lockedHeldCopy(reservation);
            OffsetDateTime checkedAt = now();
            if (checkedAt.isAfter(reservation.getPickupDeadline())) expire(reservation, copy);
            return context(reservation, checkedAt);
        }
        return context(reservation, now());
    }

    private ReservationLoanContextResponse context(BookReservation reservation, OffsetDateTime checkedAt) {
        LibraryCard card = cards.findByUserIdWithDetails(reservation.getReader().getId()).orElse(null);
        String cardNumber = card == null ? null : card.getCardNumber();
        String loanNumber = loans.findNumberByReservation(reservation.getId()).orElse(null);
        boolean converted = "FULFILLED".equals(reservation.getStatus()) || loanNumber != null
                || reservations.hasLoanLinkedToReservation(reservation.getId());
        boolean expired = !converted && ("EXPIRED".equals(reservation.getStatus())
                || ("READY_FOR_PICKUP".equals(reservation.getStatus()) && reservation.getPickupDeadline() != null
                    && checkedAt.isAfter(reservation.getPickupDeadline())));
        String message = expired ? ReservationPickupExpiredException.MESSAGE
                : (!converted && reservation.getPickupDeadline() == null ? MISSING_DEADLINE : null);
        LoanDatePreviewResponse dates = null;
        String dateError = message;
        if (!converted && !expired && message == null) {
            try { dates = calculateDates(card, checkedAt); }
            catch (ApiException error) { dateError = error.getMessage(); }
        }
        BookCopy copy = reservation.getBookCopy();
        var summary = new ReadyForPickupReservationResponse(reservation.getId(), reservation.getBook().getId(),
                reservation.getBook().getTitle(), copy == null ? null : copy.getId(), copy == null ? null : copy.getBarcode(),
                reservation.getReader().getId(), reservation.getReader().getFullName(), reservation.getStatus(),
                reservation.getReservedAt(), reservation.getPickupDeadline());
        return new ReservationLoanContextResponse(cardNumber, converted, loanNumber, dates, dateError,
                reservation.getStatus(), expired, reservation.getPickupDeadline(), checkedAt, message,
                copy == null ? null : copy.getStatus(), summary);
    }

    private boolean converted(BookReservation reservation) {
        return "FULFILLED".equals(reservation.getStatus())
                || loans.findNumberByReservation(reservation.getId()).isPresent()
                || reservations.hasLoanLinkedToReservation(reservation.getId());
    }

    private BookCopy lockedHeldCopy(BookReservation reservation) {
        BookCopy copy = copies.findForStatusChange(reservation.getBookCopy().getId()).orElseThrow(this::invalidCopy);
        if (!reservation.getBook().getId().equals(copy.getBook().getId()) || !"HELD".equals(copy.getStatus())
                || copies.hasUnreturnedLoan(copy.getId())) throw invalidCopy();
        return copy;
    }

    private void expire(BookReservation reservation, BookCopy copy) {
        // Policy chosen for S3-01.4: release to AVAILABLE; do not allocate the next waiter.
        reservation.setStatus("EXPIRED");
        reservations.saveAndFlush(reservation);
        if (copy != null) {
            copy.releaseReservationHold();
            copies.saveAndFlush(copy);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ReservationPickupExpiredException.class)
    public ReservationLoanResponse createFromReservation(Long reservationId, Long actorId, String cardNumber) {
        return createFromReservation(reservationId, actorId, cardNumber, null, null, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ReservationPickupExpiredException.class)
    public ReservationLoanResponse createFromReservation(Long reservationId, Long actorId, String cardNumber,
            LocalDate expectedBorrowDate, OffsetDateTime expectedDueAt, Integer expectedLoanDays) {
        return createFromReservation(reservationId, actorId, cardNumber,
                expectedBorrowDate, expectedDueAt, expectedLoanDays, null, false, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ReservationPickupExpiredException.class)
    public ReservationLoanResponse createFromReservation(Long reservationId, Long actorId, String cardNumber,
            LocalDate expectedBorrowDate, OffsetDateTime expectedDueAt, Integer expectedLoanDays, UUID requestId) {
        return createFromReservation(reservationId, actorId, cardNumber,
                expectedBorrowDate, expectedDueAt, expectedLoanDays, requestId, false, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ReservationPickupExpiredException.class)
    public ReservationLoanResponse createFromReservation(Long reservationId, Long actorId, String cardNumber,
            LocalDate expectedBorrowDate, OffsetDateTime expectedDueAt, Integer expectedLoanDays,
            UUID requestId, boolean overrideRequested, String overrideReason) {
        requireStaff(actorId);
        String approvedReason = validateOverrideRequest(actorId, overrideRequested, overrideReason);
        validateId(reservationId);
        String confirmed = cardNumber == null ? "" : cardNumber.strip();
        if (confirmed.isBlank() || confirmed.length() > 100
                || confirmed.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER",
                    "Vui lòng nhập mã thẻ hợp lệ, tối đa 100 ký tự.");
        }

        // Same title -> reservation -> copy lock order as reservation cancellation.
        // Direct confirmations must see committed concurrent reservation loans when checking quota.
        // Acquire the shared reader lock BEFORE any title lock to avoid reversing lock order.
        loans.findReservationReaderId(reservationId).ifPresent(reservations::lockReaderForCreation);
        // Keep the current card policy stable before loading its JPA details.
        loans.lockDirectLoanCard(confirmed);
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
        if ("EXPIRED".equals(reservation.getStatus())) throw new ReservationPickupExpiredException(reservationId);
        if (!"READY_FOR_PICKUP".equals(reservation.getStatus())) throw new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_NOT_READY", "Chỉ được lập phiếu từ đơn đặt giữ đang Chờ nhận.");

        if (reservation.getPickupDeadline() == null) throw new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_PICKUP_DEADLINE_MISSING", MISSING_DEADLINE);
        Long readerId = reservation.getReader().getId();
        var card = cards.findByUserIdWithDetails(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "LIBRARY_CARD_REQUIRED",
                        "Bạn đọc của đơn đặt giữ chưa có thẻ thư viện để đối chiếu."));
        if (!confirmed.equals(card.getCardNumber())) throw new ApiException(HttpStatus.BAD_REQUEST,
                "RESERVATION_CARD_MISMATCH", "Mã thẻ không đúng với bạn đọc sở hữu đơn đặt giữ.");
        if (reservation.getBookCopy() == null) throw invalidCopy();
        var copy = lockedHeldCopy(reservation);

        OffsetDateTime borrowedAt = now();
        if (borrowedAt.isAfter(reservation.getPickupDeadline())) {
            expire(reservation, copy);
            // Commit only the intended EXPIRED/AVAILABLE transition, then return HTTP 409.
            throw new ReservationPickupExpiredException(reservationId);
        }
        // S3-03.2: reservation conversions are loans too; never bypass card/overdue checks.
        var cardBlockReasons = cardAndOverdueReasons(card, readerId,
                LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE));
        long borrowed = loans.countUnreturnedBooksForReader(readerId);
        int maxBooks = card.getCardType() == null ? -1 : card.getCardType().getMaxBooks();
        List<BlockReason> allReasons = new ArrayList<>(cardBlockReasons);
        if (maxBooks >= 0 && borrowed >= maxBooks &&
                allReasons.stream().noneMatch(r -> "LOAN_LIMIT_REACHED".equals(r.code()))) {
            allReasons.add(new BlockReason("LOAN_LIMIT_REACHED", loanLimitMessage(borrowed, maxBooks)));
        }
        // A manager override must also honor every non-bypassable account/card restriction.
        if (approvedReason != null) {
            allReasons = new ArrayList<>(readerEligibility(confirmed, actorId).blockReasons());
        }
        if (!allReasons.isEmpty() && approvedReason == null) {
            logBlocked(requestId, "RESERVATION_CONFIRM", readerId, reservation.getReader().getFullName(),
                    card.getCardNumber(), actorId, reservationId, borrowed, maxBooks, allReasons);
            throw new ApiException(HttpStatus.CONFLICT, allReasons.get(0).code(), joinReasons(allReasons));
        }
        requirePermittedViolations(allReasons, approvedReason);
        LoanDatePreviewResponse dates = calculateDates(card, borrowedAt);
        if ((expectedBorrowDate != null && !expectedBorrowDate.equals(dates.borrowDate()))
                || (expectedDueAt != null && !expectedDueAt.isEqual(dates.dueAt()))
                || (expectedLoanDays != null && expectedLoanDays != dates.loanDays())) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_DATES_CHANGED",
                    "Ngày mượn hoặc hạn trả đã thay đổi. Vui lòng kiểm tra thông tin mới và xác nhận lại.");
        }
        if (maxBooks < 0 || maxBooks > 10) throw new ApiException(HttpStatus.CONFLICT,
                "LOAN_POLICY_NOT_CONFIGURED", "Loại thẻ chưa có giới hạn mượn hợp lệ từ 0 đến 10 sách.");
        String loanNumber = "PM-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        Long loanId = loans.insert(reservationId, readerId, actorId, loanNumber, borrowedAt);
        loans.insertItem(loanId, copy.getId(), borrowedAt, dates.dueAt());
        // insertItem has succeeded and its existing trigger has changed the copy to BORROWED.
        // Flush the reservation in this same transaction: any failure rolls back all three writes.
        reservation.setStatus("FULFILLED");
        reservations.saveAndFlush(reservation);
        if (approvedReason != null) recordOverride(requestId, "RESERVATION_CONFIRM", readerId,
                reservation.getReader().getFullName(), card.getCardNumber(), actorId, reservationId,
                borrowed, maxBooks, loanId, approvedReason, allReasons);
        return new ReservationLoanResponse(loanId, loanNumber, reservationId,
                readerId, reservation.getReader().getFullName(), card.getCardNumber(),
                bookId, reservation.getBook().getTitle(), copy.getId(), copy.getBarcode(),
                borrowedAt, "Đã lập phiếu mượn thành công từ đơn đặt giữ.", dates);
    }

    /** A POST request corresponds to one intentional card check; GET previews stay read-only. */
    @Transactional(readOnly = true)
    public ReaderLoanEligibilityResponse checkReaderAndLog(String cardNumber, Long actorId, UUID requestId) {
        ReaderLoanEligibilityResponse eligibility = readerEligibility(cardNumber, actorId);
        if (rejectionLogs != null) rejectionLogs.logEligibility(requestId, "CARD_CHECK", actorId, eligibility);
        return eligibility;
    }

    private void logBlocked(UUID requestId, String source, Long readerId, String readerName,
                            String cardNumber, Long actorId, Long reservationId,
                            long borrowed, int limit, List<BlockReason> reasons) {
        if (rejectionLogs == null || reasons.isEmpty()) return;
        rejectionLogs.log(requestId == null ? UUID.randomUUID() : requestId, source,
                readerId, readerName, cardNumber, actorId, reservationId, borrowed, limit,
                reasons.stream().map(reason -> new LoanRejectionResponse.Reason(reason.code(), reason.message())).toList());
    }

    private String validateOverrideRequest(Long actorId, boolean requested, String reason) {
        if (!requested) {
            if (reason != null && !reason.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "OVERRIDE_FLAG_REQUIRED", "Vui lòng chọn bỏ qua lần chặn trước khi nhập lý do.");
            return null;
        }
        if (actorId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập.");
        var manager = users.findById(actorId).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (manager.getRole() == null || !"LIBRARY_MANAGER".equals(manager.getRole().getCode())
                || !"ACTIVE".equals(manager.getStatus())) throw new ApiException(
                HttpStatus.FORBIDDEN, "MANAGER_OVERRIDE_REQUIRED", "Chỉ Quản lý thư viện được bỏ qua lần chặn.");
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isBlank() || normalized.length() > 500) throw new ApiException(
                HttpStatus.BAD_REQUEST, "OVERRIDE_REASON_REQUIRED",
                "Phải nhập lý do bỏ qua từ 1 đến 500 ký tự.");
        return normalized;
    }

    private void validateOverridePreview(Long actorId) {
        if (actorId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập.");
        var user = users.findById(actorId).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (user.getRole() == null || !"LIBRARY_MANAGER".equals(user.getRole().getCode())
                || !"ACTIVE".equals(user.getStatus())) throw new ApiException(HttpStatus.FORBIDDEN,
                "MANAGER_OVERRIDE_REQUIRED", "Chỉ Quản lý thư viện được kiểm tra sách cho lượt bỏ qua.");
    }

    private boolean canOverride(List<BlockReason> reasons) {
        return !reasons.isEmpty() && reasons.stream().allMatch(r -> OVERRIDABLE.contains(r.code()));
    }

    private void requirePermittedViolations(List<BlockReason> reasons, String approvedReason) {
        if (approvedReason == null) return;
        if (reasons.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,
                "OVERRIDE_NOT_NEEDED", "Không có vi phạm cần bỏ qua trong lượt mượn này.");
        if (!canOverride(reasons)) throw new ApiException(HttpStatus.CONFLICT,
                "OVERRIDE_NOT_ALLOWED", "Có điều kiện không thể bỏ qua: " + joinReasons(reasons));
        if (rejectionLogs == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                "OVERRIDE_AUDIT_UNAVAILABLE", "Nhật ký bỏ qua chưa sẵn sàng. Không được lập phiếu.");
    }

    private void recordOverride(UUID requestId, String source, Long readerId, String readerName,
            String cardNumber, Long actorId, Long reservationId, long borrowed, int maxBooks,
            Long loanId, String reason, List<BlockReason> reasons) {
        // A blocked attempt already owns its audit request_id. Retrying that same
        // loan confirmation with manager approval must append an OVERRIDDEN event,
        // not collide with or overwrite the earlier BLOCKED snapshot. The loan
        // itself remains idempotent through its original direct request UUID.
        rejectionLogs.logOverride(UUID.randomUUID(),
                source, readerId, readerName, cardNumber, actorId, reservationId,
                borrowed, maxBooks, loanId, reason, reasons.stream()
                    .map(r -> new LoanRejectionResponse.Reason(r.code(), r.message())).toList());
    }

    private void requireStaff(Long actorId) {
        requireStaff(actorId, "lập phiếu mượn");
    }

    private void requireStaff(Long actorId, String action) {
        if (actorId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED",
                "Vui lòng đăng nhập để " + action + ".");
        var actor = users.findById(actorId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (actor.getRole() == null || actor.getRole().getCode() == null
                || !Set.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN").contains(actor.getRole().getCode())
                || !"ACTIVE".equals(actor.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAFF_ROLE_REQUIRED",
                    "Chỉ nhân viên thư viện đang hoạt động mới được " + action + ".");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private LoanDatePreviewResponse calculateDates(LibraryCard card, OffsetDateTime borrowedAt) {
        if (card == null) throw new ApiException(HttpStatus.CONFLICT, "LIBRARY_CARD_REQUIRED",
                "Bạn đọc chưa có thẻ thư viện để xác định chính sách mượn.");
        var type = card.getCardType();
        if (type == null || type.getLoanDays() < 1 || type.getLoanDays() > 60) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_POLICY_NOT_CONFIGURED",
                    "Loại thẻ chưa có số ngày mượn hợp lệ từ 1 đến 60. Vui lòng cấu hình chính sách mượn.");
        }
        return configuration.calculateLoanDates(borrowedAt, type.getLoanDays(), type.getName());
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DirectLoanItemResponse previewDirectLoanItem(AddDirectLoanItemRequest request, Long actorId) {
        requireStaff(actorId);
        if (request == null || request.selectedBarcodes() == null || request.selectedBarcodes().size() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_DRAFT",
                    "Danh sách mã vạch không hợp lệ, tối đa 10 sách.");
        }
        String barcode = normalizeDraftBarcode(request.barcode());
        Set<String> selected = new HashSet<>();
        for (String value : request.selectedBarcodes()) {
            if (!selected.add(normalizeDraftBarcode(value))) throw duplicateDraftBarcode();
        }
        if (selected.contains(barcode)) throw duplicateDraftBarcode();

        // Reject a non-manager's preview override before revealing policy findings.
        if (request.overridePreview()) validateOverridePreview(actorId);
        // Reuse S3-02.1 and re-read quota for every addition. No draft is persisted.
        var reader = readerEligibility(request.cardNumber(), actorId);
        if (!reader.eligible() && !(request.overridePreview()
                && canOverride(reader.blockReasons()))) throw new ApiException(HttpStatus.CONFLICT,
                reader.reasonCode(), reader.message());
        if (!request.overridePreview() && (long) selected.size() + 1 > reader.remainingBooks()) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_DRAFT_LIMIT_EXCEEDED",
                    "Không thể thêm sách: lượt mượn sẽ vượt giới hạn. Bạn đọc đang mượn "
                            + reader.borrowedBooks() + "/" + reader.maxBooks() + " sách; chỉ còn được mượn thêm "
                            + reader.remainingBooks() + " sách. Vui lòng xóa bớt dòng hoặc kiểm tra lại thẻ.");
        }
        var copy = copies.findByBarcode(barcode).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "LOAN_DRAFT_COPY_NOT_FOUND",
                        "Không tìm thấy sách theo mã vạch đã nhập. Vui lòng kiểm tra lại."));
        // Read the actual allocation even if a stale copy status says AVAILABLE.
        // Preview remains read-only; expiration/release and conversion use their existing flows.
        var hold = reservations.findEffectiveHoldForCopy(copy.getId(), now());
        if (hold.isPresent() && !converted(hold.get())) {
            throw directLoanHoldConflict(hold.get(), reader.readerId());
        }
        if (!"AVAILABLE".equals(copy.getStatus())) {
            String status = copy.getStatus();
            String label = status == null || status.isBlank() ? "Chưa xác định" : switch (status) {
                case "BORROWED" -> "Đang mượn";
                case "HELD" -> "Đang giữ cho đặt trước";
                case "REPAIR" -> "Đang sửa chữa";
                case "REMOVED" -> "Đã loại khỏi kho";
                case "LOST" -> "Mất";
                case "DAMAGED" -> "Hư hỏng";
                default -> status;
            };
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_DRAFT_COPY_NOT_AVAILABLE",
                    "Bản sao không ở trạng thái Sẵn sàng. Trạng thái hiện tại: " + label + ".");
        }
        if (copy.getBook() == null) throw new ApiException(HttpStatus.CONFLICT, "LOAN_DRAFT_BOOK_MISSING",
                "Bản sao chưa có thông tin đầu sách. Vui lòng kiểm tra lại.");
        return new DirectLoanItemResponse(copy.getId(), copy.getBook().getId(), copy.getBarcode(),
                copy.getBook().getTitle(), reader.remainingBooks());
    }

    /** Header, every item and the existing BORROWED trigger share one transaction. */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DirectLoanResponse createDirectLoan(CreateDirectLoanRequest request, Long actorId) {
        requireStaff(actorId);
        if (request == null || request.requestId() == null || request.barcodes() == null
                || request.barcodes().isEmpty() || request.barcodes().size() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_DRAFT",
                    "Lượt mượn phải có mã xác nhận và từ 1 đến 10 sách.");
        }
        String approvedReason = validateOverrideRequest(actorId, request.overrideRequested(), request.overrideReason());
        String cardNumber = request.cardNumber() == null ? "" : request.cardNumber().strip();
        if (cardNumber.isEmpty() || cardNumber.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_CARD_NUMBER", "Vui lòng nhập mã thẻ từ 1 đến 100 ký tự.");
        List<String> barcodes = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String value : request.barcodes()) {
            String barcode = normalizeDraftBarcode(value);
            if (!unique.add(barcode)) throw duplicateDraftBarcode();
            barcodes.add(barcode);
        }
        String fingerprint = directFingerprint(cardNumber, barcodes);
        loans.lockDirectRequest(request.requestId());
        var previous = loans.findDirectRequest(request.requestId());
        if (previous.isPresent()) {
            var saved = previous.get();
            if (!actorId.equals(saved.actorId()) || !fingerprint.equals(saved.fingerprint())) {
                throw new ApiException(HttpStatus.CONFLICT, "DIRECT_LOAN_REQUEST_REUSED",
                        "Mã xác nhận đã được dùng cho lượt khác. Vui lòng bắt đầu lượt mượn mới.");
            }
            return directResult(saved.loanId(), cardNumber, actorId);
        }
        Long readerId = loans.findReaderIdByCardNumber(cardNumber).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "LIBRARY_CARD_NOT_FOUND",
                        "Không tìm thấy bạn đọc với mã thẻ này. Vui lòng kiểm tra lại mã thẻ."));
        reservations.lockReaderForCreation(readerId);
        Long lockedReader = loans.lockDirectLoanCard(cardNumber).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "LIBRARY_CARD_CHANGED",
                        "Thông tin thẻ đã thay đổi. Vui lòng kiểm tra lại thẻ."));
        if (!readerId.equals(lockedReader)) throw new ApiException(HttpStatus.CONFLICT,
                "LIBRARY_CARD_CHANGED", "Thông tin bạn đọc của thẻ đã thay đổi. Vui lòng kiểm tra lại.");

        List<LoanRepository.CopyIdentity> identities = new ArrayList<>();
        for (String barcode : barcodes) identities.add(loans.findCopyIdentity(barcode).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "LOAN_DRAFT_COPY_NOT_FOUND",
                        "Không tìm thấy bản sao có mã vạch " + barcode + ". Lượt mượn chưa được ghi.")));
        // Match reservation allocation/cancellation: sorted titles, then sorted copies.
        // Holding titles also prevents a new hold appearing between our check and insert.
        for (Long bookId : identities.stream().map(LoanRepository.CopyIdentity::bookId).distinct().sorted().toList()) {
            books.findForReservation(bookId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                    "LOAN_DRAFT_BOOK_MISSING", "Đầu sách đã thay đổi. Vui lòng kiểm tra lại lượt mượn."));
        }
        List<BookCopy> lockedCopies = new ArrayList<>();
        for (var identity : identities.stream().sorted(java.util.Comparator.comparing(
                LoanRepository.CopyIdentity::copyId)).toList()) {
            var copy = copies.findForStatusChange(identity.copyId()).orElseThrow(() -> new ApiException(
                    HttpStatus.CONFLICT, "LOAN_DRAFT_COPY_NOT_FOUND", "Bản sao đã bị xóa. Lượt mượn chưa được ghi."));
            if (copy.getBook() == null || !identity.bookId().equals(copy.getBook().getId())
                    || !unique.contains(copy.getBarcode())) throw new ApiException(HttpStatus.CONFLICT,
                    "LOAN_DRAFT_COPY_CHANGED", "Thông tin bản sao đã thay đổi. Vui lòng kiểm tra lại.");
            lockedCopies.add(copy);
        }

        // Sample eligibility/time only after all lock waits; do not trust browser previews.
        var reader = readerEligibility(cardNumber, actorId);
        List<BlockReason> violations = new ArrayList<>(reader.blockReasons());
        if (barcodes.size() > Math.max(0L, (long) reader.maxBooks() - reader.borrowedBooks())) {
            String message = "Lượt mượn " + barcodes.size() + " sách vượt giới hạn còn lại "
                    + Math.max(0L, (long) reader.maxBooks() - reader.borrowedBooks()) + " sách của bạn đọc.";
            violations.add(new BlockReason("LOAN_DRAFT_LIMIT_EXCEEDED", message));
        }
        if (!violations.isEmpty() && approvedReason == null) {
            logBlocked(request.requestId(), "DIRECT_CONFIRM", reader.readerId(), reader.readerName(),
                    reader.cardNumber(), actorId, null, reader.borrowedBooks(), reader.maxBooks(), violations);
            throw new ApiException(HttpStatus.CONFLICT, violations.get(0).code(), joinReasons(violations));
        }
        requirePermittedViolations(violations, approvedReason);
        OffsetDateTime borrowedAt = now();
        for (BookCopy copy : lockedCopies) {
            var hold = reservations.findEffectiveHoldForCopy(copy.getId(), borrowedAt);
            if (hold.isPresent() && !converted(hold.get())) throw directLoanHoldConflict(hold.get(), readerId);
            if (!"AVAILABLE".equals(copy.getStatus()) || copies.hasUnreturnedLoan(copy.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "LOAN_DRAFT_COPY_NOT_AVAILABLE",
                        "Bản sao " + copy.getBarcode() + " không còn Sẵn sàng. Lượt mượn chưa được ghi.");
            }
        }
        LibraryCard card = cards.findByCardNumberWithDetails(cardNumber).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "LIBRARY_CARD_CHANGED", "Vui lòng kiểm tra lại thẻ."));
        var dates = calculateDates(card, borrowedAt);
        try {
            Long loanId = loans.insertDirect(readerId, actorId,
                    "PM-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT), borrowedAt,
                    request.requestId(), fingerprint);
            // Keep the scanned order in the loan while acquiring locks in a deterministic order.
            for (var identity : identities) loans.insertItem(loanId, identity.copyId(), borrowedAt, dates.dueAt());
            if (approvedReason != null) recordOverride(request.requestId(), "DIRECT_CONFIRM", readerId,
                    reader.readerName(), cardNumber, actorId, null, reader.borrowedBooks(),
                    reader.maxBooks(), loanId, approvedReason, violations);
            return directResult(loanId, cardNumber, actorId);
        } catch (DataAccessException error) {
            // Throw through the transactional proxy; never swallow or commit a partial write.
            var failed = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DIRECT_LOAN_SAVE_FAILED",
                    "Không thể ghi trọn vẹn lượt mượn. Toàn bộ thay đổi của lượt đã được hủy. Vui lòng thử lại.");
            failed.initCause(error);
            throw failed;
        }
    }

    private DirectLoanResponse directResult(Long loanId, String cardNumber, Long actorId) {
        return new DirectLoanResponse(loanDetail(loanId, actorId), readerEligibility(cardNumber, actorId),
                "Đã ghi toàn bộ lượt mượn thành công. Tất cả sách trong lượt đã chuyển sang Đang mượn.");
    }

    private String directFingerprint(String cardNumber, List<String> barcodes) {
        StringBuilder value = new StringBuilder().append(cardNumber.length()).append(':').append(cardNumber);
        // Length prefixes make even unusual barcode characters unambiguous.
        for (String barcode : barcodes.stream().sorted().toList()) {
            value.append(barcode.length()).append(':').append(barcode);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private ApiException directLoanHoldConflict(BookReservation reservation, Long readerId) {
        var owner = reservation.getReader();
        boolean ownHold = owner.getId().equals(readerId);
        String readerName = owner.getFullName();
        if (readerName == null || readerName.isBlank()) readerName = "Bạn đọc của đơn đặt giữ";
        String order = "đơn đặt giữ #" + reservation.getId();
        String message = ownHold
                ? "Bản sao đang được đặt giữ cho chính bạn đọc " + readerName + " (" + order + "). "
                    + "Vui lòng lập phiếu mượn từ đơn này tại mục Sách đang chờ nhận."
                : "Bản sao đang được đặt giữ cho bạn đọc " + readerName + " (" + order + "). "
                    + "Không thể thêm vào lượt mượn của bạn đọc khác.";
        // Same staff-only scope as the pickup queue: name and order information, no contact data.
        return new ApiException(HttpStatus.CONFLICT,
                ownHold ? "LOAN_DRAFT_COPY_HELD_FOR_CURRENT_READER" : "LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER",
                message, Map.of("reservationId", reservation.getId(), "readerName", readerName,
                        "pickupDeadline", reservation.getPickupDeadline().toString(), "ownReservation", ownHold));
    }

    private String normalizeDraftBarcode(String value) {
        String barcode = value == null ? "" : value.strip();
        if (barcode.isEmpty() || barcode.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_BARCODE", "Vui lòng nhập mã vạch từ 1 đến 100 ký tự.");
        return barcode;
    }

    private ApiException duplicateDraftBarcode() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LOAN_DRAFT_BARCODE",
                "Mã vạch này đã có trong lượt mượn. Mỗi bản sao chỉ được thêm một lần.");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReaderLoanEligibilityResponse readerEligibility(String cardNumber, Long actorId) {
        requireStaff(actorId);
        String number = cardNumber == null ? "" : cardNumber.strip();
        if (number.isEmpty() || number.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER",
                    "Vui lòng nhập mã thẻ từ 1 đến 100 ký tự.");
        }
        LibraryCard card = cards.findByCardNumberWithDetails(number).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "LIBRARY_CARD_NOT_FOUND",
                        "Không tìm thấy bạn đọc với mã thẻ này. Vui lòng kiểm tra lại mã thẻ."));
        var reader = card.getUser();
        var type = card.getCardType();
        if (reader == null || type == null) {
            throw new ApiException(HttpStatus.CONFLICT, "LIBRARY_CARD_DETAILS_MISSING",
                    "Thẻ thiếu thông tin bạn đọc hoặc loại thẻ. Vui lòng kiểm tra hồ sơ thẻ.");
        }
        long borrowed = loans.countUnreturnedBooksForReader(reader.getId());
        int maxBooks = type.getMaxBooks();
        long remaining = Math.max(0L, (long) maxBooks - borrowed);
        LocalDate today = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE);
        List<BlockReason> reasons = new ArrayList<>();
        if (reader.getRole() == null || !"READER".equals(reader.getRole().getCode())) {
            reasons.add(new BlockReason("READER_ROLE_REQUIRED",
                    "Chủ thẻ không còn vai trò Bạn đọc. Vui lòng kiểm tra tài khoản."));
        }
        if (!"ACTIVE".equals(reader.getStatus())) {
            reasons.add(new BlockReason("READER_ACCOUNT_INACTIVE",
                    "Tài khoản Bạn đọc không hoạt động. Không thể tiếp tục mượn sách."));
        }
        reasons.addAll(cardAndOverdueReasons(card, reader.getId(), today));
        if (card.getIssuedAt() == null || card.getIssuedAt().isAfter(today)) {
            reasons.add(new BlockReason("LIBRARY_CARD_NOT_YET_VALID",
                    "Thẻ thư viện chưa có ngày cấp hợp lệ hoặc chưa đến ngày có hiệu lực."));
        }
        if (!type.isActive()) {
            reasons.add(new BlockReason("CARD_TYPE_INACTIVE",
                    "Loại thẻ đã ngừng hoạt động. Vui lòng kiểm tra chính sách mượn."));
        }
        if (maxBooks < 0 || maxBooks > 10) {
            reasons.add(new BlockReason("LOAN_POLICY_NOT_CONFIGURED",
                    "Loại thẻ chưa có giới hạn mượn hợp lệ từ 0 đến 10 sách."));
        }
        if (remaining == 0) {
            reasons.add(new BlockReason("LOAN_LIMIT_REACHED", loanLimitMessage(borrowed, maxBooks)));
        }
        boolean eligible = reasons.isEmpty();
        String reason = eligible ? "ELIGIBLE" : reasons.get(0).code();
        String message = eligible ? "Bạn đọc đang mượn " + borrowed + "/" + maxBooks
                + " sách. Có thể mượn thêm " + remaining + " sách." : joinReasons(reasons);
        return new ReaderLoanEligibilityResponse(reader.getId(), reader.getFullName(), card.getCardNumber(),
                type.getName(), card.getStatus(), card.getExpiresAt(), maxBooks, borrowed,
                eligible ? remaining : 0L, eligible, reason, message, reasons);
    }

    /** Independent conditions are intentionally accumulated, never mutually exclusive. */
    private List<BlockReason> cardAndOverdueReasons(LibraryCard card, Long readerId, LocalDate today) {
        List<BlockReason> reasons = new ArrayList<>();
        if ("LOCKED".equals(card.getStatus())) {
            reasons.add(new BlockReason("LIBRARY_CARD_LOCKED",
                    "Thẻ thư viện đang bị khóa. Vui lòng liên hệ người quản lý thẻ."));
        }
        // The existing S3-02 policy treats the expiry day itself as valid.
        if ("EXPIRED".equals(card.getStatus())
                || (card.getExpiresAt() != null && card.getExpiresAt().isBefore(today))) {
            reasons.add(new BlockReason("LIBRARY_CARD_EXPIRED",
                    "Thẻ thư viện đã hết hạn. Vui lòng gia hạn thẻ trước khi mượn sách."));
        }
        if (!Set.of("ACTIVE", "LOCKED", "EXPIRED").contains(
                card.getStatus() == null ? "" : card.getStatus()) || card.getExpiresAt() == null) {
            reasons.add(new BlockReason("LIBRARY_CARD_INACTIVE",
                    "Thẻ thư viện không ở trạng thái hoạt động hoặc thiếu hạn thẻ."));
        }
        long overdueLoans = loans.countOverdueUnreturnedLoansForReader(readerId, today);
        if (overdueLoans > 0) {
            reasons.add(new BlockReason("LOAN_OVERDUE_UNRETURNED",
                    "Bạn đọc có " + overdueLoans + " phiếu mượn quá hạn chưa trả. "
                    + "Vui lòng trả sách quá hạn trước khi mượn tiếp."));
        }
        // S3-03.3: only the remaining unpaid portion counts. Never trust a stale browser preview.
        // This check runs in both direct-loan confirmation and reservation-to-loan conversion.
        BigDecimal unpaid = loans.sumUnpaidFeesForReader(readerId);
        if (unpaid != null && unpaid.signum() > 0) {
            reasons.add(new BlockReason("LOAN_UNPAID_FEES",
                    "Bạn đọc còn nợ phí chưa thanh toán: " + formatVnd(unpaid)
                    + ". Vui lòng thanh toán hết trước khi mượn sách."));
        }
        return reasons;
    }

    private String formatVnd(BigDecimal amount) {
        // Vietnamese locale groups whole VND without rounding through double arithmetic.
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private String joinReasons(List<BlockReason> reasons) {
        return String.join(" ", reasons.stream().map(BlockReason::message).toList());
    }

    private String loanLimitMessage(long borrowed, int maxBooks) {
        return "Bạn đọc đang mượn " + borrowed + "/" + maxBooks
                + " sách, đã " + (borrowed > maxBooks ? "vượt" : "đạt")
                + " hạn mức của loại thẻ. Không thể mượn thêm; vui lòng trả sách trước.";
    }

    @Transactional(readOnly = true)
    public List<MyBorrowedBookResponse> myBorrowedBooks(Long readerId) {
        requireReader(readerId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE);
        return loans.findUnreturnedForReader(readerId).stream().map(item ->
                new MyBorrowedBookResponse(item.id(), item.bookTitle(), item.barcode(),
                        item.borrowedAt(), item.dueAt(), item.dueAt() == null ? null :
                        ChronoUnit.DAYS.between(today, item.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate()),
                        item.renewalsUsed(), item.maxRenewals()))
                .toList();
    }

    /** S3-05.5: validate every prior renewal guard and update the selected copy's
     * due date together with the parent loan's shared renewal counter in one transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RenewalCheckResponse checkMyLoanRenewal(Long itemId, Long readerId) {
        requireReader(readerId);
        if (itemId == null || itemId < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_ITEM_ID",
                    "Mã bản sách đang mượn không hợp lệ.");
        }
        var candidate = loans.findRenewalCandidateForReader(itemId, readerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOAN_ITEM_NOT_FOUND",
                        "Phiếu mượn không tồn tại hoặc không thuộc tài khoản của bạn."));
        if (candidate.returnedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_ALREADY_RETURNED",
                    "Phiếu này đã trả sách, không thể gia hạn.");
        }
        if (candidate.dueAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_DUE_DATE_MISSING",
                    "Phiếu chưa có hạn trả hợp lệ, không thể yêu cầu gia hạn.");
        }
        // Clock is read AFTER obtaining the locked row to avoid a stale pre-midnight check.
        OffsetDateTime checkedAt = OffsetDateTime.ofInstant(clock.instant(), LIBRARY_ZONE);
        LocalDate today = checkedAt.toLocalDate();
        if (candidate.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate().isBefore(today)) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_OVERDUE",
                    "Phiếu này đã quá hạn, không thể gia hạn.");
        }
        // Keep the S3-05.2 queue guard before any counter update.
        if (reservations.existsOtherEffectiveReservationForLoanItem(itemId, readerId, checkedAt)) {
            throw new ApiException(HttpStatus.CONFLICT, "RENEWAL_BLOCKED_BY_RESERVATION",
                    "Không thể gia hạn: đầu sách đang có Bạn đọc khác xếp hàng đặt giữ. "
                            + "Hạn trả hiện tại của bạn không thay đổi.");
        }
        var policy = loans.findRenewalPolicyForReader(itemId, readerId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "RENEWAL_POLICY_MISSING",
                        "Không tìm thấy chính sách gia hạn cho thẻ của bạn. Vui lòng liên hệ thư viện."));
        Integer limit = policy.maxRenewals();
        if (limit == null || limit <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RENEWAL_POLICY_MISSING",
                    "Loại thẻ chưa cấu hình số lần gia hạn hợp lệ. Vui lòng liên hệ thư viện.");
        }
        // S3-05.4: recheck both independent violations on every request, before
        // any quota update. The current loan is excluded in full from "other loans".
        long otherOverdueLoans = loans.countOtherOverdueUnreturnedLoansForReader(
                readerId, policy.loanId(), today);
        BigDecimal unpaidRenewalFees = loans.sumUnpaidFeesForReader(readerId);
        List<String> renewalViolations = new ArrayList<>();
        if (otherOverdueLoans > 0) {
            renewalViolations.add("Bạn có " + otherOverdueLoans
                    + " phiếu mượn khác quá hạn chưa trả. Vui lòng trả sách quá hạn.");
        }
        if (unpaidRenewalFees != null && unpaidRenewalFees.signum() > 0) {
            renewalViolations.add("Bạn còn nợ phí chưa thanh toán "
                    + formatVnd(unpaidRenewalFees) + ". Vui lòng thanh toán hết khoản phí còn nợ.");
        }
        if (!renewalViolations.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "RENEWAL_BLOCKED_BY_VIOLATIONS",
                    "Không thể gia hạn: " + String.join(" ", renewalViolations)
                            + " Hạn trả và số lần gia hạn đã dùng không thay đổi.");
        }
        if (policy.renewalsUsed() >= limit) {
            throw new ApiException(HttpStatus.CONFLICT, "RENEWAL_LIMIT_REACHED",
                    "Không thể gia hạn: đã dùng " + policy.renewalsUsed() + "/" + limit
                            + " lần gia hạn của phiếu này. Hạn trả không thay đổi.");
        }
        Integer days = policy.renewalDays();
        if (days == null || days <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RENEWAL_DAYS_NOT_CONFIGURED",
                    "Loại thẻ chưa cấu hình số ngày gia hạn hợp lệ. Vui lòng liên hệ thư viện.");
        }
        OffsetDateTime newDueAt = configuration.calculateRenewalDueAt(candidate.dueAt(), days);
        // Update due date first, then consume exactly one shared loan renewal.
        // Spring rolls back BOTH writes if the second update or the transaction fails.
        loans.updateDueAtForRenewal(itemId, readerId, candidate.dueAt(), newDueAt);
        if (loans.incrementRenewalCountIfAllowed(policy.loanId(), readerId) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "RENEWAL_LIMIT_REACHED",
                    "Không thể gia hạn: giới hạn loại thẻ vừa thay đổi hoặc đã hết lượt. "
                            + "Hạn trả và số lần gia hạn không thay đổi.");
        }
        int used = policy.renewalsUsed() + 1;
        String date = newDueAt.atZoneSameInstant(LIBRARY_ZONE)
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));
        return new RenewalCheckResponse(true,
                "Gia hạn thành công " + used + "/" + limit + " lần. Hạn trả mới: " + date + ".",
                used, limit, newDueAt);
    }

    private void requireReader(Long readerId) {
        if (readerId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED",
                "Vui lòng đăng nhập để xem sách của mình.");
        var reader = users.findById(readerId).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (reader.getRole() == null || !"READER".equals(reader.getRole().getCode())
                || !"ACTIVE".equals(reader.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "READER_ROLE_REQUIRED",
                    "Chỉ Bạn đọc đang hoạt động mới được xem sách của mình.");
        }
    }

    /** Count and rows share one snapshot; the client cannot increase the 20-row limit. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyReturnedBooksPageResponse myReturnedBooks(Long readerId, int page) {
        requireReader(readerId);
        if (page < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HISTORY_PAGE",
                "Số trang lịch sử không hợp lệ.");
        final int size = 20;
        long total = loans.countReturnedForReader(readerId);
        long offset = (long) page * size;
        return new MyReturnedBooksPageResponse(offset >= total ? List.of() :
                loans.findReturnedForReader(readerId, size, offset), page, size, total);
    }

    /** Keep the original lookup API for callers without additional filters. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LoanSearchPageResponse searchLoans(String enteredCode, int page, Long actorId) {
        return searchLoans(enteredCode, page, actorId, null, null, null);
    }

    /** S3-08.3: filter BEFORE counting and paging, retaining S3-08.2's ordering. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LoanSearchPageResponse searchLoans(String enteredCode, int page, Long actorId,
                                               String fromDate, String toDate, String status) {
        requireStaff(actorId, "tra cứu phiếu mượn");
        String code = enteredCode == null ? "" : enteredCode.trim();
        if (code.isEmpty() || code.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_CODE",
                    "Vui lòng nhập mã thẻ, mã vạch hoặc mã phiếu từ 1 đến 100 ký tự.");
        }
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_PAGE",
                    "Số trang tra cứu phải lớn hơn hoặc bằng 0.");
        }
        LocalDate start = parseLoanSearchDate(fromDate);
        LocalDate end = parseLoanSearchDate(toDate);
        if (start != null && end != null && start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_DATE_RANGE",
                    "Từ ngày không được lớn hơn Đến ngày.");
        }
        String normalizedStatus = status == null ? "" : status.trim();
        if (!normalizedStatus.isEmpty() && !"ALL".equals(normalizedStatus)
                && !Set.of("BORROWED", "PARTIALLY_RETURNED", "RETURNED", "EMPTY").contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_STATUS",
                    "Trạng thái phiếu không hợp lệ.");
        }
        if ("ALL".equals(normalizedStatus) || normalizedStatus.isEmpty()) normalizedStatus = null;
        boolean hasFilters = start != null || end != null || normalizedStatus != null;
        final int size = 20;
        long total = hasFilters
                ? loans.countLoansByCode(code, start, end, normalizedStatus)
                : loans.countLoansByCode(code);
        long offset = (long) page * size;
        Map<Long, LoanRepository.LoanSearchRow> headers = new LinkedHashMap<>();
        Map<Long, List<LoanSearchResultResponse.Item>> grouped = new LinkedHashMap<>();
        for (var row : offset >= total ? List.<LoanRepository.LoanSearchRow>of()
                : hasFilters ? loans.searchLoansByCode(code, start, end, normalizedStatus, size, offset)
                : loans.searchLoansByCode(code, size, offset)) {
            headers.putIfAbsent(row.loanId(), row);
            var items = grouped.computeIfAbsent(row.loanId(), ignored -> new ArrayList<>());
            if (row.itemId() != null) {
                items.add(new LoanSearchResultResponse.Item(row.barcode(), row.bookTitle(),
                        row.dueAt(), row.returnedAt() == null ? "BORROWED" : "RETURNED"));
            }
        }
        List<LoanSearchResultResponse> pageItems = grouped.entrySet().stream().map(entry -> {
            var header = headers.get(entry.getKey());
            var items = entry.getValue();
            long returned = items.stream().filter(item -> "RETURNED".equals(item.status())).count();
            String loanStatus = items.isEmpty() ? "EMPTY" : returned == items.size() ? "RETURNED"
                    : returned > 0 ? "PARTIALLY_RETURNED" : "BORROWED";
            return new LoanSearchResultResponse(header.loanId(), header.loanNumber(), header.cardNumber(),
                    header.readerName(), header.borrowedAt(), loanStatus, items);
        }).toList();
        // Determine the reason only for a genuinely empty search, never for a page beyond
        // the last result. The existing count/query/order and authorization stay intact.
        String emptyReason = null;
        if (total == 0L) {
            if (hasFilters && loans.countLoansByCode(code) > 0L) {
                emptyReason = "NO_LOANS_MATCH_FILTERS";
            } else if (loans.existsByLoanNumber(code)
                    || cards.existsByCardNumber(code) || copies.existsByBarcode(code)) {
                emptyReason = "NO_LOANS_FOR_CODE";
            } else {
                emptyReason = "CODE_NOT_FOUND";
            }
        }
        return new LoanSearchPageResponse(pageItems, page, size, total, emptyReason);
    }

    private static LocalDate parseLoanSearchDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim();
        if (!normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_DATE",
                    "Ngày mượn phải có định dạng yyyy-MM-dd hợp lệ.");
        }
        try {
            LocalDate parsed = LocalDate.parse(normalized);
            if (parsed.getYear() < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_DATE",
                        "Ngày mượn phải có định dạng yyyy-MM-dd hợp lệ.");
            }
            return parsed;
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_SEARCH_DATE",
                    "Ngày mượn phải có định dạng yyyy-MM-dd hợp lệ.");
        }
    }

    /** S3-09.2: overdue days are configured open days, excluding the deadline itself. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<OverdueLoanItemResponse> overdueLoans(Long actorId) {
        requireStaff(actorId, "xem danh sách phiếu mượn quá hạn");
        LocalDate today = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE);
        OffsetDateTime todayStart = today.atStartOfDay(LIBRARY_ZONE).toOffsetDateTime();
        var rows = loans.findOpenOverdue(todayStart);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<LocalDate> dueDates = rows.stream()
                .map(row -> row.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate())
                .collect(java.util.stream.Collectors.toSet());
        Map<LocalDate, Long> countedOpenDays = configuration.calculateOverdueOpenDays(dueDates, today);
        return rows.stream()
                .map(row -> {
                    LocalDate dueDate = row.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate();
                    long overdueDays = countedOpenDays.get(dueDate);
                    return new OverdueLoanItemResponse(row.loanId(), row.loanNumber(), row.itemId(),
                            row.readerId(), row.readerName(), row.readerPhone(), row.bookId(), row.bookTitle(),
                            row.dueAt(), overdueDays);
                })
                .sorted(Comparator.comparingLong(OverdueLoanItemResponse::overdueDays).reversed()
                        .thenComparing(OverdueLoanItemResponse::dueAt)
                        .thenComparing(OverdueLoanItemResponse::loanId)
                        .thenComparing(OverdueLoanItemResponse::itemId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> listLoans(Long actorId) {
        requireStaff(actorId);
        return loans.findAllForStaff();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LoanDetailResponse loanDetail(Long loanId, Long actorId) {
        boolean reader = actorId != null && users.findById(actorId)
                .map(actor -> actor.getRole() != null && "READER".equals(actor.getRole().getCode()))
                .orElse(false);
        if (reader) requireReader(actorId);
        else requireStaff(actorId);
        if (loanId == null || loanId < 1) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_LOAN_ID", "Mã phiếu mượn không hợp lệ.");
        var header = (reader ? loans.findHeaderForReader(loanId, actorId) : loans.findHeaderForStaff(loanId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", reader
                        ? "Phiếu không tồn tại hoặc bạn không có quyền truy cập."
                        : "Không tìm thấy phiếu mượn."));
        // Only load items after the scoped header succeeds. The shared item query does not bypass ownership.
        // Header and items share one DB snapshot; use the saved item timestamps, never current card policy.
        return new LoanDetailResponse(header.id(), header.loanNumber(), header.reservationId(),
                header.readerId(), header.readerName(), header.createdById(), header.createdByName(),
                header.borrowedAt(), loans.findItemsForStaff(loanId));
    }
}
