package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectLoanReservationServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T00:00:00+07:00");
    private BookCopyRepository copies;
    private BookReservationRepository reservations;
    private LoanRepository loans;
    private UserRepository users;
    private LoanService service;
    private BookCopy heldCopy;
    private BookReservation hold;

    @BeforeEach void setup() {
        copies = mock(BookCopyRepository.class);
        reservations = mock(BookReservationRepository.class);
        loans = mock(LoanRepository.class);
        users = mock(UserRepository.class);
        var cards = mock(LibraryCardRepository.class);
        service = new LoanService(mock(BookRepository.class), reservations, copies, cards, users,
                loans, mock(LibraryConfigurationService.class), Clock.fixed(NOW.toInstant(), NOW.getOffset()));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "LIBRARIAN", "Thủ thư")));
        var type = new CardType(); type.setName("Thẻ sinh viên"); type.setMaxBooks(5);
        var card = new LibraryCard(); card.setCardNumber("TV-0012"); card.setUser(user(12L, "READER", "Nguyễn Văn An"));
        card.setCardType(type); card.setIssuedAt(NOW.toLocalDate().minusDays(30));
        card.setExpiresAt(NOW.toLocalDate().plusDays(30));
        when(cards.findByCardNumberWithDetails("TV-0012")).thenReturn(Optional.of(card));
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(2L);
        for (long id = 1; id <= 3; id++) {
            Book book = mock(Book.class); when(book.getId()).thenReturn(50L);
            when(book.getTitle()).thenReturn("Lập trình Java");
            BookCopy copy = mock(BookCopy.class); when(copy.getId()).thenReturn(id);
            when(copy.getBarcode()).thenReturn("BC-" + id); when(copy.getBook()).thenReturn(book);
            when(copy.getStatus()).thenReturn("AVAILABLE");
            when(copies.findByBarcode("BC-" + id)).thenReturn(Optional.of(copy));
        }
        heldCopy = copies.findByBarcode("BC-2").orElseThrow();
        when(heldCopy.getStatus()).thenReturn("HELD");
        hold = new BookReservation(); hold.setId(42L); hold.setBookCopy(heldCopy);
        hold.setReader(user(20L, "READER", "Trần Thị Bình")); hold.setStatus("READY_FOR_PICKUP");
        hold.setPickupDeadline(NOW.plusDays(2));
        when(reservations.findEffectiveHoldForCopy(eq(2L), any(OffsetDateTime.class))).thenReturn(Optional.of(hold));
    }

    private User user(Long id, String code, String name) {
        User user = new User(); user.setId(id); user.setFullName(name); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(code); user.setRole(role); return user;
    }

    private AddDirectLoanItemRequest request(String barcode, String... selected) {
        return new AddDirectLoanItemRequest(" TV-0012 ", barcode, List.of(selected));
    }

    private void assertNoWrites() {
        verify(copies, never()).save(any());
        verify(copies, never()).saveAndFlush(any());
        verify(reservations, never()).save(any());
        verify(reservations, never()).saveAndFlush(any());
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
    }

    @Test void unreservedCopyPassesAndUsesTheInjectedClockWithoutWriting() {
        var item = service.previewDirectLoanItem(request(" BC-1 "), 3L);
        assertThat(item.barcode()).isEqualTo("BC-1");
        verify(reservations).findEffectiveHoldForCopy(1L, NOW);
        verify(loans, never()).findNumberByReservation(anyLong());
        assertNoWrites();
    }

    @Test void otherReadersHoldReportsTheCorrectOrderAndOwnerWithoutContactDetails() {
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2", "BC-1"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER");
                    assertThat(e.getMessage()).contains("Trần Thị Bình", "đơn đặt giữ #42");
                    assertThat(e.getDetails()).containsEntry("reservationId", 42L)
                            .containsEntry("readerName", "Trần Thị Bình").containsEntry("ownReservation", false)
                            .containsEntry("pickupDeadline", NOW.plusDays(2).toString());
                    assertThat(e.getDetails()).containsOnlyKeys("reservationId", "readerName", "pickupDeadline", "ownReservation");
                });
        assertNoWrites();
    }

    @Test void allocatedReservationStillBlocksAnInconsistentAvailableCopy() {
        when(heldCopy.getStatus()).thenReturn("AVAILABLE");
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER"));
        assertNoWrites();
    }

    @Test void ownHoldUsesTheSelectedReaderAndRequiresTheExistingReservationFlow() {
        hold.setReader(user(12L, "READER", "Nguyễn Văn An"));
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_HELD_FOR_CURRENT_READER");
                    assertThat(e.getMessage()).contains("chính bạn đọc Nguyễn Văn An", "đơn đặt giữ #42", "Sách đang chờ nhận");
                    assertThat(e.getDetails()).containsEntry("ownReservation", true);
                });
        assertNoWrites();
    }

    @Test void alreadyConvertedSourceOrderDoesNotCountAsAnEffectiveHold() {
        when(loans.findNumberByReservation(42L)).thenReturn(Optional.of("PM-42"));
        when(heldCopy.getStatus()).thenReturn("BORROWED");
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_NOT_AVAILABLE");
                    assertThat(e.getMessage()).contains("Đang mượn").doesNotContain("Trần Thị Bình", "#42");
                });
        assertNoWrites();
    }

    @Test void legacyLoanLinkIsAlsoExcludedFromTheHoldCheck() {
        when(reservations.hasLoanLinkedToReservation(42L)).thenReturn(true);
        when(heldCopy.getStatus()).thenReturn("AVAILABLE");
        assertThat(service.previewDirectLoanItem(request("BC-2"), 3L).barcode()).isEqualTo("BC-2");
        assertNoWrites();
    }

    @Test void heldCopyWithoutEffectiveOrderStillUsesTheExistingStatusValidation() {
        when(reservations.findEffectiveHoldForCopy(eq(2L), any(OffsetDateTime.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_NOT_AVAILABLE");
                    assertThat(e.getMessage()).contains("Đang giữ cho đặt trước").doesNotContain("#42");
                });
        assertNoWrites();
    }

    @Test void failedHoldLineDoesNotConsumeQuotaOrChangeEarlierAndLaterValidLines() {
        var selected = new ArrayList<String>();
        selected.add(service.previewDirectLoanItem(request("BC-1"), 3L).barcode());
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2", "BC-1"), 3L))
                .isInstanceOf(ApiException.class);
        selected.add(service.previewDirectLoanItem(request("BC-3", "BC-1"), 3L).barcode());
        assertThat(selected).containsExactly("BC-1", "BC-3");
        assertThat(hold.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertNoWrites();
    }

    @Test void missingOwnerNameHasAClearFallback() {
        hold.getReader().setFullName(" ");
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getMessage()).contains("Bạn đọc của đơn đặt giữ", "#42"));
    }

    @Test void readerCannotReadReservationOwnership() {
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "READER", "Bạn đọc")));
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(reservations, loans);
    }
}
