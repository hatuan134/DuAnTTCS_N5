package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.MyBorrowedBookResponse;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyBorrowedBooksServiceTest {
    LoanRepository loans = mock(LoanRepository.class);
    UserRepository users = mock(UserRepository.class);
    LoanService service;
    User reader;
    @BeforeEach void setup() {
        service = new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans,
                mock(LibraryConfigurationService.class), Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC));
        reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(reader));
    }
    MyBorrowedBookResponse item(long id, String due) {
        return new MyBorrowedBookResponse(id, "Tên sách " + id, "LIB-" + id,
                OffsetDateTime.parse("2026-10-06T23:30:00Z"), due == null ? null : OffsetDateTime.parse(due), null);
    }
    @Test void emptyList() {
        when(loans.findUnreturnedForReader(12L)).thenReturn(List.of());
        assertThat(service.myBorrowedBooks(12L)).isEmpty();
        verify(loans).findUnreturnedForReader(12L);
    }
    @Test void oneCopyKeepsSavedFieldsAndVietnamCalendar() {
        var row = item(1, "2026-10-08T18:00:00Z");
        when(loans.findUnreturnedForReader(12L)).thenReturn(List.of(row));
        var actual = service.myBorrowedBooks(12L).get(0);
        assertThat(actual.bookTitle()).isEqualTo(row.bookTitle());
        assertThat(actual.barcode()).isEqualTo(row.barcode());
        assertThat(actual.borrowedAt()).isEqualTo(row.borrowedAt());
        assertThat(actual.dueAt()).isEqualTo(row.dueAt());
        assertThat(actual.remainingDays()).isEqualTo(1L);
    }
    @Test void allCopiesHaveIndependentDaysTodayZeroPastNegativeAndLegacyNull() {
        when(loans.findUnreturnedForReader(12L)).thenReturn(List.of(
                item(1, "2026-10-08T10:00:00Z"), item(2, "2026-10-07T10:00:00Z"),
                item(3, "2026-11-01T10:00:00Z"), item(4, null)));
        var result = service.myBorrowedBooks(12L);
        assertThat(result).hasSize(4);
        assertThat(result).extracting(MyBorrowedBookResponse::remainingDays).containsExactly(0L, -1L, 24L, null);
    }
    @Test void unauthenticatedMissingInactiveAndNonReaderNeverReadLoans() {
        assertThatThrownBy(() -> service.myBorrowedBooks(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.myBorrowedBooks(99L)).isInstanceOf(ApiException.class);
        reader.setStatus("LOCKED");
        assertThatThrownBy(() -> service.myBorrowedBooks(12L)).isInstanceOf(ApiException.class);
        reader.setStatus("ACTIVE");
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            reader.getRole().setCode(role);
            assertThatThrownBy(() -> service.myBorrowedBooks(12L)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(loans);
    }
}
