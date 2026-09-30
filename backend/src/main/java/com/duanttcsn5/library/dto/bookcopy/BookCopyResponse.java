package com.duanttcsn5.library.dto.bookcopy;

import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.PhysicalCondition;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookCopyResponse(
        Long id,
        String barcode,
        Long bookId,
        String bookTitle,
        String isbn,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long shelfId,
        String shelfCode,
        String shelfName,
        LocalDate receivedDate,
        BigDecimal coverPrice,
        PhysicalCondition physicalCondition,
        String physicalConditionLabel,
        String status,
        String statusLabel
) {
    public static BookCopyResponse fromEntity(BookCopy copy) {
        var book = copy.getBook();
        var shelf = copy.getShelf();
        var warehouse = shelf.getWarehouse();
        var condition = copy.getPhysicalCondition();

        return new BookCopyResponse(
                copy.getId(),
                copy.getBarcode(),
                book.getId(),
                book.getTitle(),
                book.getIsbn(),
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                shelf.getId(),
                shelf.getCode(),
                shelf.getName(),
                copy.getReceivedDate(),
                copy.getCoverPrice(),
                condition,
                condition == null ? "Chưa ghi nhận" : condition.getLabel(),
                copy.getStatus(),
                statusLabel(copy.getStatus())
        );
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "AVAILABLE" -> "Sẵn sàng";
            case "BORROWED" -> "Đang mượn";
            case "HELD" -> "Đang giữ cho đặt trước";
            case "REPAIR" -> "Đang sửa chữa";
            case "REMOVED" -> "Đã loại khỏi kho";
            case "LOST" -> "Mất";
            case "DAMAGED" -> "Hư hỏng";
            default -> status;
        };
    }
}
