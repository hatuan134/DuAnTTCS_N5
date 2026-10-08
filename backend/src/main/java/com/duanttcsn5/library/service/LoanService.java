package com.duanttcsn5.library.service;

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
import java.util.HexFormat;
import java.util.HashSet;
import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.MyBorrowedBookResponse;
import com.duanttcsn5.library.dto.loan.RenewalCheckResponse;
import com.duanttcsn5.library.dto.loan.MyReturnedBooksPageResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
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
                        ChronoUnit.DAYS.between(today, item.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate())))
                .toList();
    }

    /** S3-05.1: checks eligibility at confirmation; does not extend or change the due date.
     *  Lock-based reread rejects an item that was returned while the confirmation was open.
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
        LocalDate today = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE);
        if (candidate.dueAt().atZoneSameInstant(LIBRARY_ZONE).toLocalDate().isBefore(today)) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_OVERDUE",
                    "Phiếu này đã quá hạn, không thể gia hạn.");
        }
        return new RenewalCheckResponse(true,
                "Phiếu đang mở và chưa quá hạn, đủ điều kiện ở bước kiểm tra. Hạn trả chưa thay đổi.");
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
