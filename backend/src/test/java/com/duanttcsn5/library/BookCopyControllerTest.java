package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCopyController;
import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.entity.PhysicalCondition;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookCopyControllerTest {
    private BookCopyService service;
    private MockMvc mvc;
    private final String valid = """
        {"barcodeMode":"MANUAL","barcode":"TV-001","warehouseId":10,"shelfId":20,"receivedDate":"2026-09-30",
         "coverPrice":85000,"physicalCondition":"GOOD"}
        """;
    private final String validAuto = """
        {"barcodeMode":"AUTO","warehouseId":10,"shelfId":20,"receivedDate":"2026-09-30",
         "coverPrice":85000,"physicalCondition":"GOOD"}
        """;
    @BeforeEach void setup() {
        service = mock(BookCopyService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BookCopyController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void createsManualAndReturnsDetails() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(response("TV-001"));
        mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.bookId").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
    @Test void acceptsAutoModeWithoutBarcodeField() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(response("TV-000001"));
        mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON).content(validAuto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barcode").value("TV-000001"));
    }
    @Test void manualModeRequiresBarcodeAndAutoModeRejectsClientBarcode() throws Exception {
        mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("\"barcode\":\"TV-001\",", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                .content(validAuto.replace("\"barcodeMode\":\"AUTO\",", "\"barcodeMode\":\"AUTO\",\"barcode\":\"TV-HACK\",")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void invalidPricesAreRejectedBeforeService() throws Exception {
        for (String price : new String[]{"-1", "10000000000", "1.234", "null", "\"abc\""}) {
            mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                    .content(valid.replace("85000", price))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void invalidDatesAreRejectedBeforeService() throws Exception {
        for (String date : new String[]{"2026-02-30", "2026-13-01", "not-a-date"}) {
            mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                    .content(valid.replace("2026-09-30", date))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("\"2026-09-30\"", "null"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsMissingRequiredFieldsAndUnknownCondition() throws Exception {
        for (String body : new String[]{"{}", valid.replace("TV-001", "   "), valid.replace("GOOD", "UNKNOWN"),
                valid.replace("\"shelfId\":20", "\"shelfId\":null")}) {
            mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void allFiveConditionsAndZeroPriceAreAccepted() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(response("TV-001"));
        for (PhysicalCondition condition : PhysicalCondition.values()) {
            mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                    .content(valid.replace("GOOD", condition.name()).replace("85000", "0")))
                    .andExpect(status().isCreated());
        }
    }
    @Test void clientCannotOverrideBookOrStatus() throws Exception {
        for (String extra : new String[]{"\"bookId\":2,", "\"status\":\"BORROWED\","}) {
            mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON)
                    .content(valid.replace("{", "{" + extra))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void duplicateResponseExposesLinkAndGetLinkWorks() throws Exception {
        when(service.create(eq(1L), any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "BARCODE_EXISTS",
                "Mã vạch đã tồn tại.", Map.of("existingCopyId", 100L, "copyUrl", "/book-copies/100")));
        when(service.getById(100L)).thenReturn(response("TV-001"));
        mvc.perform(post("/api/v1/books/1/copies").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.existingCopyId").value(100))
                .andExpect(jsonPath("$.details.copyUrl").value("/book-copies/100"));
        mvc.perform(get("/api/v1/book-copies/100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("TV-001"));
    }
    @Test void putAndPatchCannotReassignBook() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT, "BOOK_COPY_IMMUTABLE", "Không được chuyển đầu sách."))
                .when(service).rejectUpdate(100L);
        mvc.perform(put("/api/v1/book-copies/100").contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_COPY_IMMUTABLE"));
        mvc.perform(patch("/api/v1/book-copies/100").contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":2}"))
                .andExpect(status().isConflict());
    }
    private BookCopyResponse response(String barcode) {
        return new BookCopyResponse(100L, barcode, 1L, "Đầu sách A", null, 10L, "KHO-A", "Kho A",
                20L, "A01", "Kệ A01", LocalDate.of(2026, 9, 30), new BigDecimal("85000"),
                PhysicalCondition.GOOD, "Tốt", "AVAILABLE", "Sẵn sàng");
    }
}
