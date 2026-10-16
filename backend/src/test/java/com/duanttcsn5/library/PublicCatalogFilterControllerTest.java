package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.PublicCatalogFilterOptionsResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicCatalogFilterControllerTest {
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

    @Test void anonymousCanCombineAllFilters() throws Exception {
        when(service.getPublicBooks("mat biec", 6L, 2008, true)).thenReturn(List.of(book()));
        mvc.perform(get("/api/v1/books/public").param("keyword", "mat biec")
                        .param("categoryId", "6").param("publicationYear", "2008").param("availableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].availableCount").value(2));
        verify(service).getPublicBooks("mat biec", 6L, 2008, true);
    }

    @Test void noFiltersRetainsOldPublicApi() throws Exception {
        when(service.getPublicBooks(null, null, null, false)).thenReturn(List.of());
        mvc.perform(get("/api/v1/books/public")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).getPublicBooks(null, null, null, false);
    }

    @Test void readerCanFilter() throws Exception {
        when(service.getPublicBooks(null, 6L, null, false)).thenReturn(List.of(book()));
        mvc.perform(get("/api/v1/books/public").with(user("reader").roles("READER")).param("categoryId", "6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].categoryId").value(6));
    }

    @Test void anonymousCanLoadFilterOptionsWithoutOpeningManagementApi() throws Exception {
        when(service.getPublicFilterOptions()).thenReturn(new PublicCatalogFilterOptionsResponse(
                List.of(new PublicCatalogFilterOptionsResponse.CategoryOption(6L, "Văn học")), List.of(2008)));
        mvc.perform(get("/api/v1/books/public/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Văn học"))
                .andExpect(jsonPath("$.publicationYears[0]").value(2008));
        mvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/books").with(user("reader").roles("READER")))
                .andExpect(status().isForbidden());
        verify(service, never()).getAllBooks();
    }

    @Test void managementApiStillAllowsStaff() throws Exception {
        when(service.getAllBooks()).thenReturn(List.of());
        for (String role : List.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN")) {
            mvc.perform(get("/api/v1/books").with(user("staff").roles(role))).andExpect(status().isOk());
        }
    }

    @Test void malformedFiltersReturnExistingVietnameseErrorResponse() throws Exception {
        for (String[] parameter : List.of(
                new String[]{"categoryId", "abc"}, new String[]{"publicationYear", "2008.5"},
                new String[]{"availableOnly", "invalid"})) {
            mvc.perform(get("/api/v1/books/public").param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.message").value("Tham số yêu cầu không hợp lệ."));
        }
        verifyNoInteractions(service);
    }

    @Test void semanticValidationUsesExistingErrorResponse() throws Exception {
        when(service.getPublicBooks(null, 999L, null, false)).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "CATEGORY_NOT_FOUND", "Thể loại được chọn không tồn tại. Vui lòng chọn lại thể loại."));
        mvc.perform(get("/api/v1/books/public").param("categoryId", "999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    private BookResponse book() {
        return new BookResponse(101L, "9786040000001", "Mắt biếc", null, 1L, "Nguyễn Nhật Ánh", true,
                List.of(new BookResponse.BookAuthorResponse(1L, "Nguyễn Nhật Ánh", true)),
                6L, "Văn học", true, "NXB Trẻ", 2008, 200, null, OffsetDateTime.now(), 7L, true, 2L);
    }
}
