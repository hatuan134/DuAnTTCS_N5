package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.BookReservationResponse;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;

@Service
public class BookReservationService {
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    @Transactional
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
        // Hold this lock until commit. Concurrent requests for this book each see
        // the preceding committed reservation before calculating their position.
        Book book = books.findForReservation(bookId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        LibraryCard card = cards.findByUserIdWithDetails(readerId).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "LIBRARY_CARD_REQUIRED",
                        "Bạn chưa được cấp thẻ thư viện. Không thể đặt giữ đầu sách."));

        OffsetDateTime createdAt = OffsetDateTime.now(LIBRARY_ZONE).truncatedTo(ChronoUnit.MICROS);
        validateCard(card, createdAt.toLocalDate());

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

    @Transactional(readOnly = true)
    public BookReservationQueueResponse getQueueByBookId(Long bookId) {
        if (bookId == null || bookId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_ID", "Mã đầu sách không hợp lệ.");
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
            BookCopy copy = reservation.getBookCopy();
            items.add(new BookReservationQueueResponse.QueueEntry(
                    reservation.getId(), reservation.getReader().getId(), reservation.getReader().getFullName(),
                    reservation.getReservedAt(), reservation.getStatus(), position,
                    copy == null ? null : copy.getId(), copy == null ? null : copy.getBarcode()));
        }
        return new BookReservationQueueResponse(book.getId(), book.getTitle(), List.copyOf(items));
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
