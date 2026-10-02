package com.duanttcsn5.library.dto.bookcopy;

import java.util.List;

public record BulkCreateBookCopiesResponse(
        int createdCount,
        String startBarcode,
        String endBarcode,
        List<String> skippedBarcodes,
        List<BulkCreatedBookCopyResponse> createdCopies
) {
    public BulkCreateBookCopiesResponse {
        skippedBarcodes = skippedBarcodes == null ? List.of() : List.copyOf(skippedBarcodes);
        createdCopies = createdCopies == null ? List.of() : List.copyOf(createdCopies);
    }

    public BulkCreateBookCopiesResponse(int createdCount) {
        this(createdCount, null, null, List.of(), List.of());
    }

    public BulkCreateBookCopiesResponse(int createdCount, String startBarcode, String endBarcode,
            List<String> skippedBarcodes) {
        this(createdCount, startBarcode, endBarcode, skippedBarcodes, List.of());
    }
}
