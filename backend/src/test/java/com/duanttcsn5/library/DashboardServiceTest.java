package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReaderProfileRepository readerProfileRepository;

    @Mock
    private LibraryCardRepository libraryCardRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("SuaLoiTaiKhoan: tổng quan dùng số tài khoản thực, không tính bản ghi DISABLED cũ")
    void getStats_UsesExistingAccountCount() {
        when(userRepository.countExistingAccounts()).thenReturn(8L);
        when(userRepository.countActiveReaders()).thenReturn(3L);
        when(libraryCardRepository.count()).thenReturn(2L);
        when(readerProfileRepository.countByRegistrationStatus("PENDING")).thenReturn(1L);

        var stats = dashboardService.getStats();

        assertEquals(8L, stats.totalAccounts());
        assertEquals(3L, stats.activeReaders());
        assertEquals(2L, stats.issuedLibraryCards());
        assertEquals(1L, stats.pendingReaderRequests());
        verify(userRepository).countExistingAccounts();
    }
}
