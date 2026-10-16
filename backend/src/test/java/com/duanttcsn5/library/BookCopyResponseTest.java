package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.PhysicalCondition;
import com.duanttcsn5.library.entity.Shelf;
import com.duanttcsn5.library.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookCopyResponseTest {

    @Test
    void mapsAllRequiredDisplayStatuses() {
        Map<String, String> expected = Map.of(
                "AVAILABLE", "Sẵn sàng",
                "BORROWED", "Đang mượn",
                "HELD", "Đang giữ cho đặt trước",
                "REPAIR", "Đang sửa chữa",
                "REMOVED", "Đã loại khỏi kho"
        );

        expected.forEach((status, label) ->
                assertEquals(label, BookCopyResponse.fromEntity(copyWithStatus(status)).statusLabel()));
    }

    private BookCopy copyWithStatus(String status) {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("Đầu sách A");

        Warehouse warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setCode("KHO-A");
        warehouse.setName("Kho A");

        Shelf shelf = new Shelf();
        shelf.setId(20L);
        shelf.setCode("A01");
        shelf.setName("Kệ A01");
        shelf.setWarehouse(warehouse);

        BookCopy copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(100L);
        when(copy.getBarcode()).thenReturn("TV-001");
        when(copy.getBook()).thenReturn(book);
        when(copy.getShelf()).thenReturn(shelf);
        when(copy.getReceivedDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(copy.getPhysicalCondition()).thenReturn(PhysicalCondition.GOOD);
        when(copy.getStatus()).thenReturn(status);
        return copy;
    }
}
