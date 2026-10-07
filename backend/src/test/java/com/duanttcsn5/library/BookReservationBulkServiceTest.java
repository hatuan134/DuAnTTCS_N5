package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.Shelf;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.entity.Warehouse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.BookReservationService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookReservationBulkServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock BookRepository books;
    @Mock BookReservationRepository reservations;
    @Mock UserRepository users;
    @Mock LibraryCardRepository cards;
    @Mock BookCopyRepository copies;
    @Mock LibraryConfigurationService configuration;

    BookReservationService service;
    User reader;
    Book book;
    LibraryCard card;

    @BeforeEach
    void setup() {
        service = new BookReservationService(books, reservations, users, cards, copies, configuration);
        reader = new User();
        reader.setId(12L);
        reader.setFullName("Bạn đọc An");
        reader.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("READER");
        reader.setRole(role);

        book = new Book();
        book.setId(7L);
        book.setTitle("Dế Mèn phiêu lưu ký");

        card = new LibraryCard();
        card.setUser(reader);
        card.setStatus("ACTIVE");
        card.setExpiresAt(LocalDate.now(ZONE).plusDays(30));
    }

    private void eligible() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
    }

    @Test
    void createsTwoReservationsForSameTitleWhenTwoCopiesAreAvailable() {
        eligible();
        BookCopy first = availableCopy(101L, "TV-KHO-A-A01-000101");
        BookCopy second = availableCopy(102L, "TV-KHO-A-A01-000102");
        when(reservations.countActiveForReader(12L)).thenReturn(0L);
        when(copies.countAvailableByBookId(7L)).thenReturn(2L);
        when(copies.findFirstAvailableForReservation(7L))
                .thenReturn(Optional.of(first), Optional.of(second));
        when(configuration.calculateReservationPickupDeadline(any(OffsetDateTime.class)))
                .thenAnswer(invocation -> ((OffsetDateTime) invocation.getArgument(0)).plusDays(3));
        AtomicLong ids = new AtomicLong(200L);
        when(reservations.saveAndFlush(any(BookReservation.class))).thenAnswer(invocation -> {
            BookReservation reservation = invocation.getArgument(0);
            reservation.setId(ids.getAndIncrement());
            return reservation;
        });

        var response = service.reserveMany(7L, 12L, 2);

        assertThat(response.requestedQuantity()).isEqualTo(2);
        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.activeReservationCount()).isEqualTo(2);
        assertThat(response.remainingActiveSlots()).isEqualTo(1);
        assertThat(response.reservations()).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo("READY_FOR_PICKUP");
                    assertThat(item.bookId()).isEqualTo(7L);
                    assertThat(item.reservedCopy()).isNotNull();
                    assertThat(item.pickupDeadline()).isNotNull();
                });
        assertThat(first.getStatus()).isEqualTo("HELD");
        assertThat(second.getStatus()).isEqualTo("HELD");
        verify(reservations, never()).existsActiveForReaderAndBook(anyLong(), anyLong());
    }

    @Test
    void quantityCannotExceedCurrentlyAvailableCopies() {
        eligible();
        when(reservations.countActiveForReader(12L)).thenReturn(0L);
        when(copies.countAvailableByBookId(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.reserveMany(7L, 12L, 2))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RESERVATION_QUANTITY_EXCEEDS_AVAILABLE");
                    assertThat(error.getMessage()).contains("Chỉ còn 1 bản sẵn sàng");
                });

        verify(copies, never()).findFirstAvailableForReservation(anyLong());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(configuration);
    }

    @Test
    void bulkReservationStillRespectsGlobalThreeActiveReservationLimit() {
        eligible();
        when(reservations.countActiveForReader(12L)).thenReturn(2L);

        assertThatThrownBy(() -> service.reserveMany(7L, 12L, 2))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RESERVATION_LIMIT_REACHED");
                    assertThat(error.getMessage()).contains("chỉ còn 1 lượt");
                });

        verify(copies, never()).countAvailableByBookId(anyLong());
        verify(copies, never()).findFirstAvailableForReservation(anyLong());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(configuration);
    }

    @Test
    void borrowedTitleIsRejectedBeforeAllocatingAnyCopy() {
        eligible();
        when(reservations.hasUnreturnedLoanForReaderAndBook(12L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.reserveMany(7L, 12L, 1))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("BOOK_ALREADY_BORROWED"));

        verify(reservations, never()).countActiveForReader(anyLong());
        verify(copies, never()).countAvailableByBookId(anyLong());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(configuration);
    }

    private BookCopy availableCopy(long id, String barcode) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setCode("KHO-A");
        warehouse.setName("Kho A");
        Shelf shelf = new Shelf();
        shelf.setId(1L);
        shelf.setCode("A01");
        shelf.setName("Kệ Văn học");
        shelf.setWarehouse(warehouse);

        BookCopy copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "barcode", barcode);
        ReflectionTestUtils.setField(copy, "shelf", shelf);
        ReflectionTestUtils.setField(copy, "status", "AVAILABLE");
        return copy;
    }
}
