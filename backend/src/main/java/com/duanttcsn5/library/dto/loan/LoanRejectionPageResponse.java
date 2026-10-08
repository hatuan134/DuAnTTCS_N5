package com.duanttcsn5.library.dto.loan;

import java.util.List;

public record LoanRejectionPageResponse(
        List<LoanRejectionResponse> items, int page, int size, long total
) {}
