package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.dashboard.DashboardStatsResponse;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final LibraryCardRepository libraryCardRepository;

    public DashboardService(
            UserRepository userRepository,
            ReaderProfileRepository readerProfileRepository,
            LibraryCardRepository libraryCardRepository) {
        this.userRepository = userRepository;
        this.readerProfileRepository = readerProfileRepository;
        this.libraryCardRepository = libraryCardRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        return new DashboardStatsResponse(
                userRepository.count(),
                userRepository.countActiveReaders(),
                libraryCardRepository.count(),
                readerProfileRepository.countByRegistrationStatus("PENDING")
        );
    }
}
