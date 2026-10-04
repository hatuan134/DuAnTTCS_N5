package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.dto.book.MyBookReservationResponse;
import com.duanttcsn5.library.dto.book.CancelBookReservationResponse;
import com.duanttcsn5.library.dto.book.ReservationCancellationAuditResponse;
import com.duanttcsn5.library.dto.book.BookReservationQueueResponse;
import com.duanttcsn5.library.dto.book.ReadyForPickupReservationResponse;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

@Service
public class BookReservationService {
    private static final Set<String> QUEUE_FILTER_STATUSES = Set.of(
            "PENDING", "READY_FOR_PICKUP", "FULFILLED", "CANCELLED");
    private static final int MAX_ACTIVE_RESERVATIONS = 3;
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String RESERVATION_ALREADY_BORROWED_MESSAGE =
            "Không thể huỷ đơn vì sách đã được nhận và đơn đã chuyển thành phiếu mượn.";

    private final BookRepository books;
    private final BookReservationRepository reservations;
    private final UserRepository users;
    private final LibraryCardRepository cards;

    private final BookCopyRepository copies;
    private final LibraryConfigurationService configuration;

    public BookReservationService(BookRepository books, BookReservationRepository reservations,
                                  UserRepository users, LibraryCardRepository cards,
                                  BookCopyRepository copies, LibraryConfigurationService configuration) {
        this.books = books;
        this.reservations = reservations;
        this.users = users;
        this.cards = cards;
        this.copies = copies;
        this.configuration = configuration;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MyBookReservationResponse> getMyReservations(Long readerId) {
        if (readerId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED",
                    "Vui lòng đăng nhập để xem đơn đặt giữ của bạn.");
        }
        User reader = users.findById(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (reader.getRole() == null || !"READER".equals(reader.getRole().getCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "READER_ROLE_REQUIRED",
                    "Chỉ tài khoản Bạn đọc mới được xem danh sách đơn đặt giữ cá nhân.");
        }
        if (!"ACTIVE".equals(reader.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Tài khoản Bạn đọc không hoạt động.");
        }
        // Viewing history does not require an active library card.
        return reservations.findAllForReader(readerId).stream().map(reservation -> {
            Long position = "PENDING".equals(reservation.getStatus())
                    ? reservations.findPendingQueuePosition(reservation.getId()) : null;
            OffsetDateTime deadline = "READY_FOR_PICKUP".equals(reservation.getStatus())
                    && reservation.getBookCopy() != null ? reservation.getPickupDeadline() : null;
            return new MyBookReservationResponse(reservation.getId(), reservation.getBook().getId(),
                    reservation.getBook().getTitle(), reservation.getStatus(), reservation.getReservedAt(),
                    position, deadline);
        }).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookReservationResponse reserve(Long bookId, Long readerId) {
        if (readerId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED",
                    "Bạn chưa đăng nhập. Vui lòng đăng nhập để đặt giữ đầu sách.");
        }
        User reader = users.findById(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."));
        if (reader.getRole() == null || !"READER".equals(reader.getRole().getCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "READER_ROLE_REQUIRED",
                    "Chỉ tài khoản Bạn đọc mới được đặt giữ đầu sách.");
        }
        if (!"ACTIVE".equals(reader.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE",
                    "Tài khoản Bạn đọc không hoạt động. Không thể đặt giữ đầu sách.");
        }
        if (bookId == null || bookId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_ID", "Mã đầu sách không hợp lệ.");
        }
        // Acquire the reader lock first. READ_COMMITTED gives each subsequent
        // query a fresh snapshot after a concurrent creation has committed.
        reservations.lockReaderForCreation(readerId);
        // Hold this lock until commit. Concurrent requests for this book each see
        // the preceding committed reservation before calculating their position.
        Book book = books.findForReservation(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        LibraryCard card = cards.findByUserIdWithDetails(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "LIBRARY_CARD_REQUIRED",
                        "Bạn chưa được cấp thẻ thư viện. Không thể đặt giữ đầu sách."));

        OffsetDateTime createdAt = OffsetDateTime.now(LIBRARY_ZONE).truncatedTo(ChronoUnit.MICROS);
        validateCard(card, createdAt.toLocalDate());
        validateNotCurrentlyBorrowed(readerId, book);
        validateReservationLimits(readerId, book);

        BookReservation reservation = new BookReservation(book, reader, "PENDING");
        reservation.setReservedAt(createdAt);
        BookCopy selected = copies.findFirstAvailableForReservation(bookId).orElse(null);
        BookReservationResponse.ReservedCopy copyInfo = null;
        if (selected != null) {
            // Calculate first: invalid calendar configuration must not consume a copy.
            OffsetDateTime deadline = configuration.calculateReservationPickupDeadline(createdAt);
            selected.holdForReservation();
            copies.save(selected);
            reservation.setBookCopy(selected);
            reservation.setStatus("READY_FOR_PICKUP");
            reservation.setPickupDeadline(deadline);
            var shelf = selected.getShelf();
            var warehouse = shelf.getWarehouse();
            copyInfo = new BookReservationResponse.ReservedCopy(selected.getId(), selected.getBarcode(),
                    warehouse.getCode(), warehouse.getName(), shelf.getCode(), shelf.getName());
        }
        BookReservation saved = reservations.saveAndFlush(reservation);
        Long position = selected == null ? reservations.findPendingQueuePosition(saved.getId()) : null;
        String message = selected == null
                ? "Đặt giữ thành công. Chưa có bản Sẵn sàng. Vị trí hiện tại trong hàng đợi: " + position + "."
                : "Đặt giữ thành công. Thư viện đã dành một bản sách cho bạn. Vui lòng đến nhận trước hạn hiển thị.";
        return new BookReservationResponse(saved.getId(), book.getId(), saved.getStatus(),
                saved.getReservedAt(), position, message, saved.getPickupDeadline(), copyInfo);
    }

    private void validateNotCurrentlyBorrowed(Long readerId, Book book) {
        // Check every loan item of this title, not just the copy we might allocate.
        // Run before queue insertion, copy selection, or pickup deadline calculation.
        if (reservations.hasUnreturnedLoanForReaderAndBook(readerId, book.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOK_ALREADY_BORROWED",
                    "Bạn đang mượn đầu sách “" + book.getTitle() + "” và chưa trả. "
                            + "Vui lòng trả hết các bản đang mượn của đầu sách này trước khi đặt giữ.");
        }
    }

    private void validateReservationLimits(Long readerId, Book book) {
        // Prefer the title-specific reason even when this reader is at the limit.
        // Both checks run before looking up/holding a copy or inserting a queue row.
        if (reservations.existsActiveForReaderAndBook(readerId, book.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_ALREADY_ACTIVE",
                    "Bạn đã có đơn đặt giữ đang hiệu lực cho đầu sách “" + book.getTitle()
                            + "”. Không thể đặt giữ trùng đầu sách này.");
        }
        if (reservations.countActiveForReader(readerId) >= MAX_ACTIVE_RESERVATIONS) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_LIMIT_REACHED",
                    "Bạn đã đạt giới hạn tối đa 3 đơn đặt giữ đang hiệu lực (đang chờ hoặc chờ đến nhận). "
                            + "Vui lòng huỷ hoặc hoàn tất một đơn trước khi đặt giữ thêm.");
        }
    }

    @Transactional(readOnly = true)
    public BookReservationQueueResponse getQueueByBookId(Long bookId) {
        return getQueueByBookId(bookId, null);
    }

    @Transactional(readOnly = true)
    public BookReservationQueueResponse getQueueByBookId(Long bookId, String status) {
        if (bookId == null || bookId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_ID", "Mã đầu sách không hợp lệ.");
        }
        String filter = status == null || status.isBlank() ? null : status.trim();
        if (filter != null && !QUEUE_FILTER_STATUSES.contains(filter)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_STATUS",
                    "Trạng thái lọc không hợp lệ. Chọn Đang xếp hàng, Đang chờ nhận, "
                            + "Đã chuyển thành phiếu mượn hoặc Đã huỷ.");
        }
        Book book = books.findById(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));

        // Calculate every current position from a single ordered query. Avoid
        // counting each row separately while concurrent reservations change.
        List<BookReservation> ordered = reservations.findAllForQueueByBookId(bookId);
        List<BookReservationQueueResponse.QueueEntry> items = new ArrayList<>(ordered.size());
        long pendingPosition = 0;
        for (BookReservation reservation : ordered) {
            Long position = "PENDING".equals(reservation.getStatus()) ? ++pendingPosition : null;
            // Count current PENDING positions before applying the display filter.
            // Retain the repository's (reservedAt, id) order for every status.
            if (filter != null && !filter.equals(reservation.getStatus())) continue;
            BookCopy copy = reservation.getBookCopy();
            items.add(new BookReservationQueueResponse.QueueEntry(
                    reservation.getId(), reservation.getReader().getId(), reservation.getReader().getFullName(),
                    reservation.getReservedAt(), reservation.getStatus(), position,
                    copy == null ? null : copy.getId(), copy == null ? null : copy.getBarcode(),
                    cancellationAudit(reservation)));
        }
        return new BookReservationQueueResponse(book.getId(), book.getTitle(), List.copyOf(items));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelMine(Long reservationId, Long readerId) {
        if (readerId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập để huỷ đơn.");
        }
        User reader = users.findById(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (reader.getRole() == null || !"READER".equals(reader.getRole().getCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "READER_ROLE_REQUIRED",
                    "Chỉ Bạn đọc mới được tự huỷ đơn đặt giữ.");
        }
        if (!"ACTIVE".equals(reader.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Tài khoản Bạn đọc không hoạt động.");
        }
        if (reservationId == null || reservationId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_ID", "Mã đơn đặt giữ không hợp lệ.");
        }
        Long bookId = reservations.findBookIdForCancellation(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ."));
        // Keep the same lock order as reserve/cancelByStaff so reservation creation,
        // staff cancellation and reader cancellation cannot reorder the FIFO queue.
        books.findForReservation(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        BookReservation target = reservations.findForCancellation(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ."));
        // Hide another reader's order, including its current status.
        if (!readerId.equals(target.getReader().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ.");
        }
        if (!bookId.equals(target.getBook().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_CHANGED",
                    "Đơn đã thay đổi. Vui lòng tải lại danh sách.");
        }
        rejectFulfilledReservation(target);
        if (!"PENDING".equals(target.getStatus()) && !"READY_FOR_PICKUP".equals(target.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE",
                    "Chỉ được tự huỷ đơn đang xếp hàng hoặc đang chờ nhận. Vui lòng tải lại danh sách.");
        }
        if ("PENDING".equals(target.getStatus()) && target.getBookCopy() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE",
                    "Đơn đang xếp hàng có dữ liệu cấp bản sao không hợp lệ. Vui lòng tải lại danh sách.");
        }

        OffsetDateTime releasedAt = OffsetDateTime.now(LIBRARY_ZONE).truncatedTo(ChronoUnit.MICROS);
        BookCopy copy = null;
        BookReservation next = null;
        OffsetDateTime nextDeadline = null;
        if (target.getBookCopy() != null) {
            if (!"READY_FOR_PICKUP".equals(target.getStatus())) throw invalidHeldCopy();
            copy = copies.findForStatusChange(target.getBookCopy().getId()).orElseThrow(this::invalidHeldCopy);
            rejectLoanLinkedReservation(target);
            if (!bookId.equals(copy.getBook().getId()) || !"HELD".equals(copy.getStatus())
                    || copies.hasUnreturnedLoan(copy.getId())) throw invalidHeldCopy();
            next = reservations.findNextPendingForCancellation(bookId).orElse(null);
            if (next != null) {
                // Calculate before mutating anything. Invalid calendar configuration
                // must leave the current hold and queue untouched.
                nextDeadline = configuration.calculateReservationPickupDeadline(releasedAt);
                if (nextDeadline == null || !nextDeadline.isAfter(releasedAt)
                        || !nextDeadline.isAfter(next.getReservedAt())) {
                    throw new ApiException(HttpStatus.CONFLICT, "INVALID_PICKUP_DEADLINE",
                            "Không xác định được hạn nhận hợp lệ cho người tiếp theo. Đơn chưa được huỷ.");
                }
            }
        }

        // Remove the old active READY allocation before assigning the same physical
        // copy to the next waiter. The partial unique index therefore stays valid.
        target.setStatus("CANCELLED");
        reservations.saveAndFlush(target);
        if (copy != null && next != null) {
            next.setStatus("READY_FOR_PICKUP");
            next.setBookCopy(copy);
            next.setPickupDeadline(nextDeadline);
            reservations.saveAndFlush(next);
            // The physical copy remains HELD because ownership of the hold changed.
        } else if (copy != null) {
            copy.releaseReservationHold();
            copies.saveAndFlush(copy);
        }
        // Queue positions and available-copy counts are derived from current
        // PENDING reservations and AVAILABLE book_copies, so no denormalized counter
        // needs to be updated here.
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancelBookReservationResponse cancelByStaff(Long reservationId, Long actorId, String reason) {
        if (actorId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập để huỷ đơn.");
        }
        User actor = users.findById(actorId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (actor.getRole() == null || actor.getRole().getCode() == null || !Set.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN")
                .contains(actor.getRole().getCode()) || !"ACTIVE".equals(actor.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAFF_ROLE_REQUIRED",
                    "Chỉ nhân viên thư viện đang hoạt động mới được huỷ đơn thay bạn đọc.");
        }
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isBlank() || normalized.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CANCELLATION_REASON",
                    "Lý do huỷ phải có từ 1 đến 500 ký tự, không chỉ gồm khoảng trắng.");
        }
        if (reservationId == null || reservationId < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_ID", "Mã đơn đặt giữ không hợp lệ.");
        }
        Long bookId = reservations.findBookIdForCancellation(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ."));
        // Creation already locks this title. Use the same lock first, then the
        // target reservation/copy/next waiter, so new arrivals cannot jump FIFO.
        books.findForReservation(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        BookReservation target = reservations.findForCancellation(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ."));
        if (!bookId.equals(target.getBook().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_CHANGED", "Đơn đã thay đổi. Vui lòng tải lại.");
        }
        rejectFulfilledReservation(target);
        if (!"PENDING".equals(target.getStatus()) && !"READY_FOR_PICKUP".equals(target.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE",
                    "Chỉ được huỷ đơn Đang xếp hàng hoặc Đang chờ nhận. Đơn này đã đổi trạng thái.");
        }
        OffsetDateTime cancelledAt = OffsetDateTime.now(LIBRARY_ZONE).truncatedTo(ChronoUnit.MICROS);
        BookCopy copy = null;
        BookReservation next = null;
        OffsetDateTime nextDeadline = null;
        if (target.getBookCopy() != null) {
            if (!"READY_FOR_PICKUP".equals(target.getStatus())) throw invalidHeldCopy();
            copy = copies.findForStatusChange(target.getBookCopy().getId()).orElseThrow(this::invalidHeldCopy);
            rejectLoanLinkedReservation(target);
            if (!bookId.equals(copy.getBook().getId()) || !"HELD".equals(copy.getStatus())
                    || copies.hasUnreturnedLoan(copy.getId())) throw invalidHeldCopy();
            next = reservations.findNextPendingForCancellation(bookId).orElse(null);
            if (next != null) {
                // Validate the calendar before changing any reservation or copy.
                nextDeadline = configuration.calculateReservationPickupDeadline(cancelledAt);
                if (nextDeadline == null || !nextDeadline.isAfter(cancelledAt)
                        || !nextDeadline.isAfter(next.getReservedAt())) {
                    throw new ApiException(HttpStatus.CONFLICT, "INVALID_PICKUP_DEADLINE",
                            "Không xác định được hạn nhận hợp lệ cho người tiếp theo. Đơn chưa được huỷ.");
                }
            }
        }
        target.cancelByStaff(actor, cancelledAt, normalized);
        // Release the partial unique READY copy allocation BEFORE assigning it
        // to the next order. Both flushes still roll back if any later step fails.
        reservations.saveAndFlush(target);
        String outcome = "NO_COPY";
        if (copy != null && next != null) {
            next.setStatus("READY_FOR_PICKUP");
            next.setBookCopy(copy);
            next.setPickupDeadline(nextDeadline);
            reservations.saveAndFlush(next);
            outcome = "TRANSFERRED"; // Physical copy remains HELD throughout.
        } else if (copy != null) {
            copy.releaseReservationHold();
            copies.saveAndFlush(copy);
            outcome = "AVAILABLE";
        }
        String message = "Đã huỷ đơn #" + target.getId() + " và ghi nhận lý do huỷ.";
        return new CancelBookReservationResponse(target.getId(), bookId, target.getStatus(),
                cancellationAudit(target), copy == null ? null : copy.getId(), copy == null ? null : copy.getBarcode(),
                outcome, next == null ? null : next.getId(), next == null ? null : next.getReader().getFullName(),
                nextDeadline, message);
    }

    private void rejectFulfilledReservation(BookReservation reservation) {
        if ("FULFILLED".equals(reservation.getStatus())) {
            throw reservationAlreadyBorrowed();
        }
    }

    private void rejectLoanLinkedReservation(BookReservation reservation) {
        if (reservations.hasLoanLinkedToReservation(reservation.getId())) {
            throw reservationAlreadyBorrowed();
        }
    }

    private ApiException reservationAlreadyBorrowed() {
        return new ApiException(HttpStatus.CONFLICT, "RESERVATION_ALREADY_BORROWED",
                RESERVATION_ALREADY_BORROWED_MESSAGE);
    }

    private ApiException invalidHeldCopy() {
        return new ApiException(HttpStatus.CONFLICT, "RESERVATION_COPY_CONFLICT",
                "Bản sao đang giữ không còn phù hợp để giải phóng. Vui lòng tải lại và đối chiếu dữ liệu.");
    }

    private ReservationCancellationAuditResponse cancellationAudit(BookReservation reservation) {
        if (reservation.getCancelledAt() == null) return null;
        return new ReservationCancellationAuditResponse(reservation.getCancelledBy(), reservation.getCancelledByName(),
                reservation.getCancelledAt(), reservation.getCancellationReason());
    }

    @Transactional(readOnly = true)
    public List<ReadyForPickupReservationResponse> getReadyForPickup() {
        return reservations.findReadyForPickup().stream().map(this::toReadyResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReadyForPickupReservationResponse getReadyForPickupById(Long reservationId) {
        if (reservationId == null || reservationId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_ID",
                    "Mã đơn đặt giữ không hợp lệ.");
        }
        BookReservation reservation = reservations.findReadyForPickupById(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "READY_RESERVATION_NOT_FOUND",
                        "Không tìm thấy đơn đặt giữ đang chờ nhận. Đơn có thể đã đổi trạng thái."));
        return toReadyResponse(reservation);
    }

    private ReadyForPickupReservationResponse toReadyResponse(BookReservation reservation) {
        BookCopy copy = reservation.getBookCopy();
        return new ReadyForPickupReservationResponse(
                reservation.getId(), reservation.getBook().getId(), reservation.getBook().getTitle(),
                copy == null ? null : copy.getId(), copy == null ? null : copy.getBarcode(),
                reservation.getReader().getId(), reservation.getReader().getFullName(),
                reservation.getStatus(), reservation.getReservedAt(), reservation.getPickupDeadline());
    }

    private void validateCard(LibraryCard card, LocalDate today) {
        if ("LOCKED".equals(card.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LIBRARY_CARD_LOCKED",
                    "Thẻ thư viện đang bị khóa. Không thể đặt giữ đầu sách.");
        }
        // expiresAt is an inclusive date in the existing card model.
        if ("EXPIRED".equals(card.getStatus())
                || (card.getExpiresAt() != null && card.getExpiresAt().isBefore(today))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LIBRARY_CARD_EXPIRED",
                    "Thẻ thư viện đã hết hạn. Vui lòng gia hạn thẻ trước khi đặt giữ.");
        }
        if (!"ACTIVE".equals(card.getStatus()) || card.getExpiresAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LIBRARY_CARD_INACTIVE",
                    "Thẻ thư viện không ở trạng thái hoạt động. Không thể đặt giữ đầu sách.");
        }
    }
}
