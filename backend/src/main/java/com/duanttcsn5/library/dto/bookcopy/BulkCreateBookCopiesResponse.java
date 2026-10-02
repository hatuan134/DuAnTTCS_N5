package com.duanttcsn5.library.dto.bookcopy;

import java.util.List;

public record BulkCreateBookCopiesResponse(int createdCount, String startBarcode, String endBarcode,
        List<String> skippedBarcodes) {
    public BulkCreateBookCopiesResponse(int createdCount) {
        this(createdCount, null, null, List.of());
    }
}
