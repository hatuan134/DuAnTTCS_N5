package com.duanttcsn5.library.dto.bookcopy;

import java.util.List;

public record BulkBarcodePreviewResponse(long startNumber, String startBarcode, String endBarcode,
        int quantity, List<String> skippedBarcodes) {
    public BulkBarcodePreviewResponse(long startNumber, String startBarcode, String endBarcode, int quantity) {
        this(startNumber, startBarcode, endBarcode, quantity, List.of());
    }
}
