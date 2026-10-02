package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCopyController;
import com.duanttcsn5.library.dto.bookcopy.BulkCreateBookCopiesResponse;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookCopyBulkControllerTest {

    private BookCopyService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(BookCopyService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BookCopyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsOneTenAndFiftyCopies() throws Exception {
        for (int quantity : new int[]{1, 10, 50}) {
            when(service.createBulk(eq(1L), any())).thenReturn(new BulkCreateBookCopiesResponse(quantity));

            mvc.perform(post("/api/v1/books/1/copies/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(valid(quantity)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.createdCount").value(quantity));
        }
    }

    @Test
    void rejectsZeroFiftyOneAndNonIntegerQuantityBeforeService() throws Exception {
        for (String quantity : new String[]{"0", "51", "10.5"}) {
            mvc.perform(post("/api/v1/books/1/copies/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(valid(quantity)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingWarehouseShelfOrDateBeforeService() throws Exception {
        String base = valid(10);
        for (String body : new String[]{
                base.replace("\"warehouseId\":10", "\"warehouseId\":null"),
                base.replace("\"shelfId\":20", "\"shelfId\":null"),
                base.replace("\"receivedDate\":\"2026-09-30\"", "\"receivedDate\":null")
        }) {
            mvc.perform(post("/api/v1/books/1/copies/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedDateBeforeService() throws Exception {
        for (String date : new String[]{"2026-02-30", "2026-13-01", "not-a-date"}) {
            mvc.perform(post("/api/v1/books/1/copies/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(valid(10).replace("2026-09-30", date)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    private String valid(int quantity) {
        return valid(Integer.toString(quantity));
    }

    private String valid(String quantity) {
        return """
                {"quantity":%s,"warehouseId":10,"shelfId":20,"receivedDate":"2026-09-30"}
                """.formatted(quantity);
    }
}
