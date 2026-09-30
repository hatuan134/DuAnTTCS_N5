package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCopyController;
import com.duanttcsn5.library.controller.LibraryConfigurationController;
import com.duanttcsn5.library.dto.bookcopy.BarcodeMode;
import com.duanttcsn5.library.dto.bookcopy.CreateBookCopyRequest;
import com.duanttcsn5.library.dto.libraryconfig.WarehouseRequest;
import com.duanttcsn5.library.entity.PhysicalCondition;
import com.duanttcsn5.library.service.BookCopyService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookCopyPermissionTest {
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurity {}
    private AnnotationConfigApplicationContext context;
    private BookCopyController copies;
    private LibraryConfigurationController locations;
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurity.class);
        context.registerBean(BookCopyService.class, () -> mock(BookCopyService.class));
        context.registerBean(LibraryConfigurationService.class, () -> mock(LibraryConfigurationService.class));
        context.registerBean(BookCopyController.class);
        context.registerBean(LibraryConfigurationController.class);
        context.refresh();
        copies = context.getBean(BookCopyController.class);
        locations = context.getBean(LibraryConfigurationController.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    private void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-user", "unused", AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
    @Test void readerCannotCreateReadOrUpdateCopiesOrReadLocations() {
        role("READER");
        assertThrows(AccessDeniedException.class, () -> copies.create(1L, request()));
        assertThrows(AccessDeniedException.class, () -> copies.get(1L));
        assertThrows(AccessDeniedException.class, () -> copies.rejectUpdate(1L));
        assertThrows(AccessDeniedException.class, () -> locations.getWarehouses());
        assertThrows(AccessDeniedException.class, () -> locations.getShelves(null));
        verifyNoInteractions(context.getBean(BookCopyService.class));
    }
    @Test void staffCanCreateAndReadCopiesAndReadLocations() {
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            role(role);
            assertDoesNotThrow(() -> copies.create(1L, request()));
            assertDoesNotThrow(() -> copies.get(1L));
            assertDoesNotThrow(() -> locations.getWarehouses());
            assertDoesNotThrow(() -> locations.getShelves(null));
        }
    }
    @Test void librarianDoesNotGainWarehouseWritePermission() {
        role("LIBRARIAN");
        assertThrows(AccessDeniedException.class, () -> locations.createWarehouse(
                new WarehouseRequest("TEST", "Kho thử", ""), null, null));
        assertThrows(AccessDeniedException.class, () -> locations.deleteShelf(1L, null, null));
    }
    private CreateBookCopyRequest request() {
        return new CreateBookCopyRequest(BarcodeMode.MANUAL, "TV-001", 1L, 1L, LocalDate.of(2026, 9, 30),
                BigDecimal.ZERO, PhysicalCondition.NEW, null, null);
    }
}
