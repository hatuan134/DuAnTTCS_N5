package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

/** A completed return of one physical copy, including copies in partially returned loans. */
public record MyReturnedBookResponse(Long id, String bookTitle, String barcode,
        String loanNumber, OffsetDateTime borrowedAt, OffsetDateTime returnedAt) {}
