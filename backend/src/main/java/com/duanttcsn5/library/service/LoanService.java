package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse;
import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.dto.loan.DirectLoanItemResponse;
import java.util.HashSet;
import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
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
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String MISSING_DEADLINE =
            "Đơn chưa có hạn nhận hợp lệ. Vui lòng đối chiếu dữ liệu trước khi lập phiếu mượn.";

    @Autowired
    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans, LibraryConfigurationService configuration) {
        this(books, reservations, copies, cards, users, loans, configuration, Clock.system(LIBRARY_ZONE));
    }

    public LoanService(BookRepository books, BookReservationRepository reservations,
                       BookCopyRepository copies, LibraryCardRepository cards,
                       UserRepository users, LoanRepository loans, LibraryConfigurationService configuration, Clock clock) {
        this.books = books;
        this.reservations = reservations;
        this.copies = copies;
        this.cards = cards;
        this.users = users;
        this.loans = loans;
        this.configuration = configuration;
        this.clock = clock;
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
        requireStaff(actorId);
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
        LoanDatePreviewResponse dates = calculateDates(card, borrowedAt);
        if ((expectedBorrowDate != null && !expectedBorrowDate.equals(dates.borrowDate()))
                || (expectedDueAt != null && !expectedDueAt.isEqual(dates.dueAt()))
                || (expectedLoanDays != null && expectedLoanDays != dates.loanDays())) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_DATES_CHANGED",
                    "Ngày mượn hoặc hạn trả đã thay đổi. Vui lòng kiểm tra thông tin mới và xác nhận lại.");
        }
        String loanNumber = "PM-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        Long loanId = loans.insert(reservationId, readerId, actorId, loanNumber, borrowedAt);
        loans.insertItem(loanId, copy.getId(), borrowedAt, dates.dueAt());
        // insertItem has succeeded and its existing trigger has changed the copy to BORROWED.
        // Flush the reservation in this same transaction: any failure rolls back all three writes.
        reservation.setStatus("FULFILLED");
        reservations.saveAndFlush(reservation);
        return new ReservationLoanResponse(loanId, loanNumber, reservationId,
                readerId, reservation.getReader().getFullName(), card.getCardNumber(),
                bookId, reservation.getBook().getTitle(), copy.getId(), copy.getBarcode(),
                borrowedAt, "Đã lập phiếu mượn thành công từ đơn đặt giữ.", dates);
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

        // Reuse S3-02.1 and re-read quota for every addition. No draft is persisted.
        var reader = readerEligibility(request.cardNumber(), actorId);
        if (!reader.eligible()) throw new ApiException(HttpStatus.CONFLICT,
                reader.reasonCode(), reader.message());
        if ((long) selected.size() + 1 > reader.remainingBooks()) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_DRAFT_LIMIT_EXCEEDED",
                    "Không thể thêm sách: lượt mượn sẽ vượt giới hạn. Bạn đọc chỉ còn được mượn thêm "
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
        String reason = "ELIGIBLE";
        String message = "Bạn đọc đủ điều kiện mượn thêm " + remaining + " sách.";
        if (reader.getRole() == null || !"READER".equals(reader.getRole().getCode())) {
            reason = "READER_ROLE_REQUIRED";
            message = "Chủ thẻ không còn vai trò Bạn đọc. Vui lòng kiểm tra tài khoản.";
        } else if (!"ACTIVE".equals(reader.getStatus())) {
            reason = "READER_ACCOUNT_INACTIVE";
            message = "Tài khoản Bạn đọc không hoạt động. Không thể tiếp tục mượn sách.";
        } else if ("LOCKED".equals(card.getStatus())) {
            reason = "LIBRARY_CARD_LOCKED";
            message = "Thẻ thư viện đang bị khóa. Vui lòng liên hệ người quản lý thẻ.";
        } else if ("EXPIRED".equals(card.getStatus())
                || (card.getExpiresAt() != null && card.getExpiresAt().isBefore(today))) {
            reason = "LIBRARY_CARD_EXPIRED";
            message = "Thẻ thư viện đã hết hạn. Vui lòng gia hạn thẻ trước khi mượn sách.";
        } else if (!"ACTIVE".equals(card.getStatus()) || card.getExpiresAt() == null) {
            reason = "LIBRARY_CARD_INACTIVE";
            message = "Thẻ thư viện không ở trạng thái hoạt động hoặc thiếu hạn thẻ.";
        } else if (card.getIssuedAt() == null || card.getIssuedAt().isAfter(today)) {
            reason = "LIBRARY_CARD_NOT_YET_VALID";
            message = "Thẻ thư viện chưa có ngày cấp hợp lệ hoặc chưa đến ngày có hiệu lực.";
        } else if (!type.isActive()) {
            reason = "CARD_TYPE_INACTIVE";
            message = "Loại thẻ đã ngừng hoạt động. Vui lòng kiểm tra chính sách mượn.";
        } else if (maxBooks < 0 || maxBooks > 10) {
            reason = "LOAN_POLICY_NOT_CONFIGURED";
            message = "Loại thẻ chưa có giới hạn mượn hợp lệ từ 0 đến 10 sách.";
        } else if (remaining == 0) {
            reason = "LOAN_LIMIT_REACHED";
            message = "Bạn đọc đã đạt giới hạn mượn của loại thẻ. Vui lòng trả sách trước khi mượn thêm.";
        }
        boolean eligible = "ELIGIBLE".equals(reason);
        return new ReaderLoanEligibilityResponse(reader.getId(), reader.getFullName(), card.getCardNumber(),
                type.getName(), card.getStatus(), card.getExpiresAt(), maxBooks, borrowed,
                eligible ? remaining : 0L, eligible, reason, message);
    }

    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> listLoans(Long actorId) {
        requireStaff(actorId);
        return loans.findAllForStaff();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LoanDetailResponse loanDetail(Long loanId, Long actorId) {
        requireStaff(actorId);
        if (loanId == null || loanId < 1) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_LOAN_ID", "Mã phiếu mượn không hợp lệ.");
        var header = loans.findHeaderForStaff(loanId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Không tìm thấy phiếu mượn."));
        // Header and items share one DB snapshot; use the saved item timestamps, never current card policy.
        return new LoanDetailResponse(header.id(), header.loanNumber(), header.reservationId(),
                header.readerId(), header.readerName(), header.createdById(), header.createdByName(),
                header.borrowedAt(), loans.findItemsForStaff(loanId));
    }
}
