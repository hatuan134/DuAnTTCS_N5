package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookReservationServiceTest {
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

    @BeforeEach void setup() {
        service = new BookReservationService(books, reservations, users, cards, copies, configuration);
        reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        book = new Book(); book.setId(7L); book.setTitle("Dế Mèn phiêu lưu ký");
        card = new LibraryCard(); card.setUser(reader); card.setStatus("ACTIVE");
        card.setExpiresAt(LocalDate.now(ZONE).plusDays(30));
    }

    private void eligible() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
    }

    private void saved() {
        when(reservations.saveAndFlush(any())).thenAnswer(invocation -> {
            BookReservation reservation = invocation.getArgument(0);
            reservation.setId(100L);
            return reservation;
        });
        when(reservations.findPendingQueuePosition(100L)).thenReturn(3L);
    }

    private void rejected(String code) {
        assertThatThrownBy(() -> service.reserve(7L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
        verify(reservations, never()).saveAndFlush(any());
        verify(reservations, never()).findPendingQueuePosition(any());
    }

    @Test void guestCannotReserve() {
        assertThatThrownBy(() -> service.reserve(7L, null))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(401);
                    assertThat(error.getMessage()).contains("chưa đăng nhập");
                });
        verifyNoInteractions(books, reservations, users, cards);
    }

    @Test void staffCannotReserveEvenWhenCalledDirectly() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        for (String role : new String[]{"ADMIN", "LIBRARY_MANAGER", "LIBRARIAN"}) {
            reader.getRole().setCode(role);
            rejected("READER_ROLE_REQUIRED");
        }
        verifyNoInteractions(books, cards);
    }

    @Test void inactiveAccountCannotReserve() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        reader.setStatus("LOCKED");
        rejected("ACCOUNT_INACTIVE");
    }

    @Test void expiredDateRejectsEvenIfStatusStillActive() {
        eligible(); card.setExpiresAt(LocalDate.now(ZONE).minusDays(1));
        rejected("LIBRARY_CARD_EXPIRED");
    }

    @Test void expiredStatusRejectsEvenIfDateInFuture() {
        eligible(); card.setStatus("EXPIRED"); rejected("LIBRARY_CARD_EXPIRED");
    }

    @Test void lockedCardRejectsWithSpecificReason() {
        eligible(); card.setStatus("LOCKED"); rejected("LIBRARY_CARD_LOCKED");
    }

    @Test void disabledCardRejects() {
        eligible(); card.setStatus("DISABLED"); rejected("LIBRARY_CARD_INACTIVE");
    }

    @Test void missingCardRejects() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.empty());
        rejected("LIBRARY_CARD_REQUIRED");
    }

    @Test void cardWithoutExpiryRejects() {
        eligible(); card.setExpiresAt(null); rejected("LIBRARY_CARD_INACTIVE");
    }

    @Test void validCardCreatesTitleReservationAndReturnsPosition() {
        eligible(); saved();
        OffsetDateTime before = OffsetDateTime.now(ZONE).minusSeconds(1);
        var response = service.reserve(7L, 12L);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.bookId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.queuePosition()).isEqualTo(3L);
        assertThat(response.message()).contains("thành công", "3");
        assertThat(response.reservedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(OffsetDateTime.now(ZONE));
        var order = inOrder(books, reservations);
        order.verify(books).findForReservation(7L);
        order.verify(reservations).saveAndFlush(any());
        order.verify(reservations).findPendingQueuePosition(100L);
        verify(reservations).saveAndFlush(argThat(value -> value.getBook() == book
                && value.getReader() == reader && value.getPickupDeadline() == null
                && value.getCancellationReason() == null));
    }

    @Test void expiryTodayIsStillValid() {
        eligible(); saved(); card.setExpiresAt(LocalDate.now(ZONE));
        assertThat(service.reserve(7L, 12L).status()).isEqualTo("PENDING");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, 1, 2})
    void zeroOneOrTwoActiveReservationsAllowAnother(long count) {
        eligible(); saved();
        when(reservations.countActiveForReader(12L)).thenReturn(count);
        assertThat(service.reserve(7L, 12L).status()).isEqualTo("PENDING");
        var order = inOrder(reservations, books, copies);
        order.verify(reservations).lockReaderForCreation(12L);
        order.verify(books).findForReservation(7L);
        order.verify(reservations).hasUnreturnedLoanForReaderAndBook(12L, 7L);
        order.verify(reservations).existsActiveForReaderAndBook(12L, 7L);
        order.verify(reservations).countActiveForReader(12L);
        order.verify(copies).findFirstAvailableForReservation(7L);
        order.verify(reservations).saveAndFlush(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {3, 4})
    void atOrAboveThreeRejectsWithoutQueueOrCopyChanges(long count) {
        eligible();
        when(reservations.countActiveForReader(12L)).thenReturn(count);
        assertThatThrownBy(() -> service.reserve(7L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("RESERVATION_LIMIT_REACHED");
                    assertThat(error.getMessage()).contains("tối đa 3");
                });
        verifyNoInteractions(copies, configuration);
        verify(reservations, never()).saveAndFlush(any());
        verify(reservations, never()).findPendingQueuePosition(any());
    }

    @Test void duplicateTitleReasonWinsWithoutCheckingLimitOrConsumingCopy() {
        eligible();
        when(reservations.existsActiveForReaderAndBook(12L, 7L)).thenReturn(true);
        assertThatThrownBy(() -> service.reserve(7L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("RESERVATION_ALREADY_ACTIVE");
                    assertThat(error.getMessage()).contains("Dế Mèn phiêu lưu ký");
                });
        verify(reservations, never()).countActiveForReader(any());
        verify(reservations, never()).saveAndFlush(any());
        verify(reservations, never()).findPendingQueuePosition(any());
        verifyNoInteractions(copies, configuration);
    }

    @Test void retryAfterOldReservationBecomesInactiveCanCreate() {
        eligible(); saved();
        when(reservations.existsActiveForReaderAndBook(12L, 7L)).thenReturn(true, false);
        rejected("RESERVATION_ALREADY_ACTIVE");
        assertThat(service.reserve(7L, 12L).status()).isEqualTo("PENDING");
        verify(reservations, times(1)).saveAndFlush(any());
    }

    @Test void unreturnedCopyOfTitleRejectsBeforeLimitsQueueOrCopySelection() {
        eligible();
        when(reservations.hasUnreturnedLoanForReaderAndBook(12L, 7L)).thenReturn(true);
        assertThatThrownBy(() -> service.reserve(7L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("BOOK_ALREADY_BORROWED");
                    assertThat(error.getMessage()).contains("Dế Mèn phiêu lưu ký", "chưa trả");
                });
        verifyNoInteractions(copies, configuration);
        verify(reservations, never()).saveAndFlush(any());
        verify(reservations, never()).findPendingQueuePosition(any());
        verify(reservations, never()).existsActiveForReaderAndBook(any(), any());
        verify(reservations, never()).countActiveForReader(any());
    }

    @Test void retryAfterAllBorrowedCopiesAreReturnedCanReserve() {
        eligible(); saved();
        when(reservations.hasUnreturnedLoanForReaderAndBook(12L, 7L)).thenReturn(true, false);
        rejected("BOOK_ALREADY_BORROWED");
        verifyNoInteractions(copies, configuration);
        assertThat(service.reserve(7L, 12L).status()).isEqualTo("PENDING");
        verify(reservations, times(1)).saveAndFlush(any());
    }

    @Test void invalidBookIdRejects() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        assertThatThrownBy(() -> service.reserve(0L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo("INVALID_BOOK_ID"));
        verifyNoInteractions(books, reservations, cards);
    }

    private BookCopy availableCopy(Long id) {
        Warehouse warehouse = new Warehouse(); warehouse.setCode("KHO-A"); warehouse.setName("Kho A");
        Shelf shelf = new Shelf(); shelf.setCode("A01"); shelf.setName("Kệ Văn học"); shelf.setWarehouse(warehouse);
        BookCopy copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "barcode", "LIB-" + id);
        ReflectionTestUtils.setField(copy, "shelf", shelf);
        ReflectionTestUtils.setField(copy, "status", "AVAILABLE");
        return copy;
    }

    private void saveReservationOnly() {
        when(reservations.saveAndFlush(any())).thenAnswer(invocation -> {
            BookReservation value = invocation.getArgument(0); value.setId(100L); return value;
        });
    }

    @Test void availableCopyIsHeldAndPickupDetailsAreReturned() {
        eligible(); saveReservationOnly();
        BookCopy copy = availableCopy(101L);
        OffsetDateTime deadline = OffsetDateTime.now(ZONE).plusDays(5);
        when(copies.findFirstAvailableForReservation(7L)).thenReturn(Optional.of(copy));
        when(configuration.calculateReservationPickupDeadline(any())).thenReturn(deadline);
        var response = service.reserve(7L, 12L);
        assertThat(copy.getStatus()).isEqualTo("HELD");
        assertThat(response.status()).isEqualTo("READY_FOR_PICKUP");
        assertThat(response.queuePosition()).isNull();
        assertThat(response.pickupDeadline()).isEqualTo(deadline);
        assertThat(response.reservedCopy().copyId()).isEqualTo(101L);
        assertThat(response.reservedCopy().barcode()).isEqualTo("LIB-101");
        assertThat(response.reservedCopy().warehouseName()).isEqualTo("Kho A");
        assertThat(response.reservedCopy().shelfCode()).isEqualTo("A01");
        verify(reservations).saveAndFlush(argThat(value -> value.getBookCopy() == copy
                && "READY_FOR_PICKUP".equals(value.getStatus()) && deadline.equals(value.getPickupDeadline())));
        var order = inOrder(books, copies, configuration, reservations);
        order.verify(books).findForReservation(7L);
        order.verify(copies).findFirstAvailableForReservation(7L);
        order.verify(configuration).calculateReservationPickupDeadline(any());
        order.verify(copies).save(copy);
        order.verify(reservations).saveAndFlush(any());
        verify(reservations, never()).findPendingQueuePosition(any());
    }

    @Test void multipleAvailableCopiesStillAllocateOnlyOne() {
        eligible(); saveReservationOnly();
        BookCopy first = availableCopy(101L), second = availableCopy(102L);
        when(copies.findFirstAvailableForReservation(7L)).thenReturn(Optional.of(first));
        when(configuration.calculateReservationPickupDeadline(any())).thenReturn(OffsetDateTime.now(ZONE).plusDays(5));
        service.reserve(7L, 12L);
        verify(copies, times(1)).findFirstAvailableForReservation(7L);
        verify(copies, times(1)).save(first);
        assertThat(first.getStatus()).isEqualTo("HELD");
        assertThat(second.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test void noCopyRemainsPendingWithNoPickupInformation() {
        eligible(); saved();
        when(copies.findFirstAvailableForReservation(7L)).thenReturn(Optional.empty());
        var response = service.reserve(7L, 12L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.queuePosition()).isEqualTo(3L);
        assertThat(response.pickupDeadline()).isNull();
        assertThat(response.reservedCopy()).isNull();
        verify(copies, never()).save(any());
        verifyNoInteractions(configuration);
    }

    @Test void calendarFailureDoesNotChangeCopyOrCreateReservation() {
        eligible(); BookCopy copy = availableCopy(101L);
        when(copies.findFirstAvailableForReservation(7L)).thenReturn(Optional.of(copy));
        when(configuration.calculateReservationPickupDeadline(any())).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE", "Lịch chưa đầy đủ."));
        rejected("WEEKLY_SCHEDULE_INCOMPLETE");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        verify(copies, never()).save(any());
    }

    @Test void secondRequestWaitsWhenOnlyCopyHasBeenAllocated() {
        eligible(); saveReservationOnly();
        BookCopy copy = availableCopy(101L);
        when(copies.findFirstAvailableForReservation(7L)).thenReturn(Optional.of(copy), Optional.empty());
        when(configuration.calculateReservationPickupDeadline(any())).thenReturn(OffsetDateTime.now(ZONE).plusDays(5));
        when(reservations.findPendingQueuePosition(100L)).thenReturn(1L);
        User other = new User(); other.setId(13L); other.setStatus("ACTIVE"); other.setRole(reader.getRole());
        LibraryCard otherCard = new LibraryCard(); otherCard.setUser(other); otherCard.setStatus("ACTIVE");
        otherCard.setExpiresAt(LocalDate.now(ZONE).plusDays(30));
        when(users.findById(13L)).thenReturn(Optional.of(other));
        when(cards.findByUserIdWithDetails(13L)).thenReturn(Optional.of(otherCard));
        assertThat(service.reserve(7L, 12L).status()).isEqualTo("READY_FOR_PICKUP");
        assertThat(service.reserve(7L, 13L).status()).isEqualTo("PENDING");
        verify(copies, times(1)).save(copy);
        verify(configuration, times(1)).calculateReservationPickupDeadline(any());
    }

    @Test void nonexistentBookRejects() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.empty());
        rejected("BOOK_NOT_FOUND");
        verifyNoInteractions(cards);
    }
}
