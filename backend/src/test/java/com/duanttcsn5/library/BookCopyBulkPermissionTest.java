package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCopyController;
import com.duanttcsn5.library.dto.bookcopy.BulkCreateBookCopiesRequest;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BookCopyBulkPermissionTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    private AnnotationConfigApplicationContext context;
    private BookCopyController controller;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurity.class);
        context.registerBean(BookCopyService.class, () -> mock(BookCopyService.class));
        context.registerBean(BookCopyController.class);
        context.refresh();
        controller = context.getBean(BookCopyController.class);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void readerCannotCreateBulkCopies() {
        role("READER");
        assertThrows(AccessDeniedException.class, () -> controller.createBulk(1L, request()));
    }

    @Test
    void librarianManagerAndAdminCanAccessBulkEndpoint() {
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            role(role);
            assertDoesNotThrow(() -> controller.createBulk(1L, request()));
        }
    }

    private void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-user", "unused", AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    private BulkCreateBookCopiesRequest request() {
        return new BulkCreateBookCopiesRequest(
                BigDecimal.TEN,
                10L,
                20L,
                LocalDate.of(2026, 9, 30));
    }
}
