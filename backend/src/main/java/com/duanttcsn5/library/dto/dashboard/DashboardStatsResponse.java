package com.duanttcsn5.library.dto.dashboard;

public record DashboardStatsResponse(
        long totalAccounts,
        long activeReaders,
        long issuedLibraryCards,
        long pendingReaderRequests
) {
}
