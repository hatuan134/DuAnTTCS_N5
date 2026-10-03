package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.PublicCatalogPageResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.BookCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicCatalogPaginationControllerTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, BookCatalogController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
    static class TestConfig {
        @Bean BookCatalogService bookCatalogService() { return mock(BookCatalogService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private BookCatalogService service;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        service = context.getBean(BookCatalogService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void cleanup() { context.close(); }

    @Test void anonymousCanSearchWithDefaultPaginationAndRelevance() throws Exception {
        when(service.searchPublicBooks(null, null, null, false, 0, 20, "relevance"))
                .thenReturn(new PublicCatalogPageResponse(List.of(book()), 0, 20, 21, 2, true, false, "relevance"));
        mvc.perform(get("/api/v1/books/public/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Mắt biếc"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.sort").value("relevance"));
        verify(service).searchPublicBooks(null, null, null, false, 0, 20, "relevance");
    }

    @Test void readerCanKeepKeywordFiltersAndSortOnNextPage() throws Exception {
        when(service.searchPublicBooks("mat biec", 6L, 2008, true, 1, 20, "publicationYear"))
                .thenReturn(new PublicCatalogPageResponse(List.of(book()), 1, 20, 21, 2, false, true, "publicationYear"));
        mvc.perform(get("/api/v1/books/public/search").with(user("reader").roles("READER"))
                        .param("keyword", "mat biec").param("categoryId", "6")
                        .param("publicationYear", "2008").param("availableOnly", "true")
                        .param("page", "1").param("sort", "publicationYear"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.sort").value("publicationYear"));
        verify(service).searchPublicBooks("mat biec", 6L, 2008, true, 1, 20, "publicationYear");
    }

    @Test void invalidPageSizeAndSortUseExistingErrorFormat() throws Exception {
        for (String[] item : List.of(new String[]{"page", "-1", "INVALID_PAGE"},
                new String[]{"size", "21", "INVALID_PAGE_SIZE"},
                new String[]{"size", "0", "INVALID_PAGE_SIZE"},
                new String[]{"sort", "unknown", "INVALID_CATALOG_SORT"})) {
            int page = "page".equals(item[0]) ? Integer.parseInt(item[1]) : 0;
            int size = "size".equals(item[0]) ? Integer.parseInt(item[1]) : 20;
            String sort = "sort".equals(item[0]) ? item[1] : "relevance";
            when(service.searchPublicBooks(null, null, null, false, page, size, sort))
                    .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, item[2], "Tham số tra cứu không hợp lệ."));
            mvc.perform(get("/api/v1/books/public/search").param(item[0], item[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(item[2]))
                    .andExpect(jsonPath("$.message").value("Tham số tra cứu không hợp lệ."));
        }
    }

    @Test void malformedPageAndSizeReturnVietnameseBadRequest() throws Exception {
        for (String[] item : List.of(new String[]{"page", "abc"}, new String[]{"page", "1.5"},
                new String[]{"size", "abc"}, new String[]{"page", "2147483648"})) {
            mvc.perform(get("/api/v1/books/public/search").param(item[0], item[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        verifyNoInteractions(service);
    }

    @Test void existingListAndDetailApisStayCompatible() throws Exception {
        when(service.getPublicBooks(null, null, null, false)).thenReturn(List.of(book()));
        when(service.getPublicBookById(101L)).thenReturn(book());
        mvc.perform(get("/api/v1/books/public")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101));
        mvc.perform(get("/api/v1/books/public/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101));
    }

    @Test void publicSearchDoesNotOpenManagementPermissions() throws Exception {
        mvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/books").with(user("reader").roles("READER")))
                .andExpect(status().isForbidden());
        when(service.getAllBooks()).thenReturn(List.of());
        for (String role : List.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN")) {
            mvc.perform(get("/api/v1/books").with(user("staff").roles(role))).andExpect(status().isOk());
        }
    }

    private BookResponse book() {
        return new BookResponse(101L, "9786040000001", "Mắt biếc", null, 1L, "Nguyễn Nhật Ánh", true,
                List.of(new BookResponse.BookAuthorResponse(1L, "Nguyễn Nhật Ánh", true)),
                6L, "Văn học", true, "NXB Trẻ", 2008, 200, null, null, 2L, true, 1L);
    }
}
