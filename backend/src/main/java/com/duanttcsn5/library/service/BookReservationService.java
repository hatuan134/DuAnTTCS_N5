package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookRepository;
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

@Service
public class BookReservationService {
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookRepository books;
    private final BookReservationRepository reservations;
    private final UserRepository users;
    private final LibraryCardRepository cards;

    public BookReservationService(BookRepository books, BookReservationRepository reservations,
                                  UserRepository users, LibraryCardRepository cards) {
        this.books = books;
        this.reservations = reservations;
        this.users = users;
        this.cards = cards;
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
        BookReservation saved = reservations.saveAndFlush(reservation);
        long position = reservations.findPendingQueuePosition(saved.getId());
        return new BookReservationResponse(saved.getId(), book.getId(), saved.getStatus(),
                saved.getReservedAt(), position,
                "Đặt giữ thành công. Vị trí hiện tại trong hàng đợi: " + position + ".");
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
