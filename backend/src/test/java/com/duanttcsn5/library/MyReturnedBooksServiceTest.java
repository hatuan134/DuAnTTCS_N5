package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.MyReturnedBookResponse;
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

class MyReturnedBooksServiceTest {
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
    MyReturnedBookResponse item(long id) {
        return new MyReturnedBookResponse(id, "Tên sách " + id, "LIB-" + id, "PM-" + id,
                OffsetDateTime.parse("2026-10-01T10:00:00Z"), OffsetDateTime.parse("2026-10-08T10:00:00Z"));
    }
    @Test void fixedPageSizeAndReaderScopeForEmptyUnderTwentyTwentyAndTwentyOne() {
        for (int count : new int[]{0, 7, 20, 21}) {
            reset(loans);
            when(loans.countReturnedForReader(12L)).thenReturn((long) count);
            var items = java.util.stream.LongStream.range(1, Math.min(count, 20) + 1).mapToObj(this::item).toList();
            when(loans.findReturnedForReader(12L, 20, 0L)).thenReturn(items);
            var result = service.myReturnedBooks(12L, 0);
            assertThat(result.total()).isEqualTo(count);
            assertThat(result.page()).isZero(); assertThat(result.size()).isEqualTo(20);
            assertThat(result.items()).hasSize(Math.min(count, 20));
            verify(loans).countReturnedForReader(12L);
            if (count == 0) verify(loans, never()).findReturnedForReader(anyLong(), anyInt(), anyLong());
            else verify(loans).findReturnedForReader(12L, 20, 0L);
        }
    }
    @Test void laterPagesUseLongOffsetAndPreserveSavedFields() {
        when(loans.countReturnedForReader(12L)).thenReturn(41L);
        when(loans.findReturnedForReader(12L, 20, 20L)).thenReturn(List.of(item(21)));
        when(loans.findReturnedForReader(12L, 20, 40L)).thenReturn(List.of(item(41)));
        assertThat(service.myReturnedBooks(12L, 1).items()).containsExactly(item(21));
        assertThat(service.myReturnedBooks(12L, 2).items()).containsExactly(item(41));
        assertThat(service.myReturnedBooks(12L, 3).items()).isEmpty();
        verify(loans, never()).findReturnedForReader(12L, 20, 60L);
        // Multiplication must never overflow to a negative SQL OFFSET.
        when(loans.countReturnedForReader(12L)).thenReturn(Long.MAX_VALUE);
        when(loans.findReturnedForReader(12L, 20, 42949672940L)).thenReturn(List.of());
        assertThat(service.myReturnedBooks(12L, Integer.MAX_VALUE).page()).isEqualTo(Integer.MAX_VALUE);
        verify(loans).findReturnedForReader(12L, 20, 42949672940L);
    }
    @Test void negativePageRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.myReturnedBooks(12L, -1)).isInstanceOfSatisfying(ApiException.class,
                e -> { assertThat(e.getStatus().value()).isEqualTo(400); assertThat(e.getCode()).isEqualTo("INVALID_HISTORY_PAGE"); });
        verifyNoInteractions(loans);
    }
    @Test void unauthorizedInactiveAndStaffCannotReadHistory() {
        assertThatThrownBy(() -> service.myReturnedBooks(null, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.myReturnedBooks(99L, 0)).isInstanceOf(ApiException.class);
        reader.setStatus("LOCKED");
        assertThatThrownBy(() -> service.myReturnedBooks(12L, 1)).isInstanceOf(ApiException.class);
        reader.setStatus("ACTIVE");
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            reader.getRole().setCode(role);
            assertThatThrownBy(() -> service.myReturnedBooks(12L, 2)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(loans);
    }
}
