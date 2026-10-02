package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCopyController;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookCopyUpdateControllerTest {
    private MockMvc mvc;
    private BookCopyRepository copies;
    private BookCopy copy;
    private final String body = """
            {"warehouseId":11,"shelfId":21,"physicalCondition":"OLD","notes":"Bìa xước"}
            """;
    @BeforeEach void setup() {
        copies = mock(BookCopyRepository.class);
        var shelves = mock(ShelfRepository.class);
        var service = new BookCopyService(copies, mock(BookRepository.class), shelves, mock(BookCopyLifecycleRepository.class));
        Warehouse warehouse = new Warehouse(); warehouse.setId(11L); warehouse.setCode("B"); warehouse.setName("Kho B");
        Shelf shelf = new Shelf(); shelf.setId(21L); shelf.setWarehouse(warehouse); shelf.setCode("B01");
        Book book = new Book(); book.setId(1L); book.setTitle("Sách A");
        copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", 100L); ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "barcode", "TV-000001"); ReflectionTestUtils.setField(copy, "status", "AVAILABLE");
        copy.updateDetails(shelf, PhysicalCondition.GOOD, "Cũ");
        when(copies.findById(100L)).thenReturn(Optional.of(copy));
        when(shelves.findForCopyCreation(21L)).thenReturn(Optional.of(shelf));
        mvc = MockMvcBuilders.standaloneSetup(new BookCopyController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void putAndPatchReturnUpdatedDetailsAndGetReturnsSameData() throws Exception {
        for (var method : new HttpMethod[]{HttpMethod.PUT, HttpMethod.PATCH}) {
            mvc.perform(request(method, "/api/v1/book-copies/100").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.warehouseId").value(11))
                    .andExpect(jsonPath("$.shelfId").value(21)).andExpect(jsonPath("$.physicalCondition").value("OLD"))
                    .andExpect(jsonPath("$.notes").value("Bìa xước"))
                    .andExpect(jsonPath("$.barcode").value("TV-000001"))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"));
        }
        mvc.perform(get("/api/v1/book-copies/100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Bìa xước"));
    }
    @Test void directBarcodeOverrideIsRejectedAndGetStillShowsOriginalData() throws Exception {
        for (var method : new HttpMethod[]{HttpMethod.PUT, HttpMethod.PATCH}) {
            for (String barcode : new String[]{"TV-HACK", "", "TV-000001"}) {
                mvc.perform(request(method, "/api/v1/book-copies/100").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("{", "{\"barcode\":\"" + barcode + "\",")))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
        }
        mvc.perform(get("/api/v1/book-copies/100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("TV-000001"))
                .andExpect(jsonPath("$.physicalCondition").value("GOOD"))
                .andExpect(jsonPath("$.notes").value("Cũ"));
        verify(copies, never()).flush();
    }
    @Test void invalidInputAndForbiddenFieldsNeverWrite() throws Exception {
        for (String invalid : new String[]{"{}", body.replace("11", "null"), body.replace("21", "0"),
                body.replace("OLD", "UNKNOWN"), body.replace("\"OLD\"", "null"),
                body.replace("Bìa xước", "x".repeat(2001)), body.replace("{", "{\"bookId\":2,"),
                body.replace("{", "{\"status\":\"REPAIR\","), "{not-json}"}) {
            mvc.perform(put("/api/v1/book-copies/100").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("Cũ", copy.getNotes()); verify(copies, never()).flush();
    }
    @Test void wrongWarehouseAndUnknownCopyAreRejected() throws Exception {
        mvc.perform(put("/api/v1/book-copies/100").contentType(MediaType.APPLICATION_JSON).content(body.replace("11", "999")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SHELF_WAREHOUSE_MISMATCH"));
        mvc.perform(put("/api/v1/book-copies/999").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        verify(copies, never()).flush();
    }
}
