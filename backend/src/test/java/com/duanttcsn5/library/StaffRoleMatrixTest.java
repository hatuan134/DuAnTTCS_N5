package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Controller-level permission regression tests, no database required. */
class StaffRoleMatrixTest {
    private static String permit(Class<?> type, String method) {
        Method m = Arrays.stream(type.getMethods())
                .filter(it -> it.getName().equals(method))
                .findFirst().orElseThrow();
        PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
        assertNotNull(guard, type.getSimpleName() + "." + method + " must check roles");
        return guard.value();
    }

    @Test void onlyAdminMutatesStaffAccounts() {
        assertTrue(permit(UserManagementController.class, "getAccounts").contains("LIBRARY_MANAGER"));
        assertTrue(permit(UserManagementController.class, "getAccounts").contains("LIBRARIAN"));
        assertEquals("hasRole('ADMIN')", permit(UserManagementController.class, "createAccount"));
        assertEquals("hasRole('ADMIN')", permit(UserManagementController.class, "deleteAccount"));
        assertEquals("hasRole('ADMIN')", permit(UserManagementController.class, "updateAccount"));
    }

    @Test void managerCanApproveCardsButNotEditCatalog() {
        assertTrue(permit(LibraryCardController.class, "approve").contains("LIBRARY_MANAGER"));
        assertFalse(permit(AuthorController.class, "createAuthor").contains("LIBRARY_MANAGER"));
        assertFalse(permit(CategoryController.class, "createCategory").contains("LIBRARY_MANAGER"));
    }

    @Test void dashboardNotForReader() {
        assertFalse(permit(DashboardController.class, "getStats").contains("READER"));
        assertTrue(permit(DashboardController.class, "getStats").contains("ADMIN"));
    }

    @Test void staffCanReadAuditButNotChangeIt() {
        assertTrue(permit(AuditLogController.class, "search").contains("LIBRARIAN"));
        assertTrue(permit(AuditLogController.class, "search").contains("LIBRARY_MANAGER"));
        assertEquals("hasRole('ADMIN')", permit(AuditLogController.class, "rejectDelete"));
    }
}
