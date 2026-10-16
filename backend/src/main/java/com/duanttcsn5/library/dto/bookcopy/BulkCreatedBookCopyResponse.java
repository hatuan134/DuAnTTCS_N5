package com.duanttcsn5.library.dto.bookcopy;

import com.duanttcsn5.library.entity.Shelf;

import java.time.LocalDate;

/**
 * Thông tin tối thiểu của đúng một bản sao vừa được tạo trong một lô.
 * DTO này được dựng ngay trong transaction tạo lô, từ chính mã vạch đã INSERT
 * thành công, nên không lẫn các bản sao cũ của cùng đầu sách.
 */
public record BulkCreatedBookCopyResponse(
        String barcode,
        Long bookId,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long shelfId,
        String shelfCode,
        String shelfName,
        LocalDate receivedDate
) {
    public static BulkCreatedBookCopyResponse of(String barcode, Long bookId, Shelf shelf, LocalDate receivedDate) {
        var warehouse = shelf.getWarehouse();
        return new BulkCreatedBookCopyResponse(
                barcode,
                bookId,
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                shelf.getId(),
                shelf.getCode(),
                shelf.getName(),
                receivedDate
        );
    }
}
