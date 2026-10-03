package com.duanttcsn5.library.dto.book;

public record BookAvailableCopyLocationResponse(
        Long copyId,
        String barcode,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long shelfId,
        String shelfCode,
        String shelfName
) {}
