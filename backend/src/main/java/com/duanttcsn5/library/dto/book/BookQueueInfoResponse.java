package com.duanttcsn5.library.dto.book;

import java.time.LocalDate;

public record BookQueueInfoResponse(
        long queueCount,
        LocalDate earliestExpectedReturnDate,
        String expectedReturnNotice,
        boolean hasOverdueCopies
) {}
