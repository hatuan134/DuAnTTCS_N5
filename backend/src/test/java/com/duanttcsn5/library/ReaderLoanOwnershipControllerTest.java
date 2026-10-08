package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT filter, method security and service; database repositories are mocked. */
class ReaderLoanOwnershipControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LoanController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean LoanRepository loans() { return mock(LoanRepository.class); }
        @Bean LoanService service(UserRepository users, LoanRepository loans) {
            return new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                    mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans,
                    mock(LibraryConfigurationService.class), Clock.systemUTC());
        }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    LoanRepository loans;
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        loans = context.getBean(LoanRepository.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    User token(long id, String roleCode) {
        String value = "token-" + id;
        Jwt jwt = Jwt.withTokenValue(value).header("alg", "HS256").subject(Long.toString(id)).claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode(value)).thenReturn(jwt);
        User user = new User(); user.setId(id); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(user));
        return user;
    }
    @Test void twoReadersCanOpenOnlyOwnLoanAndSpoofedIdentityIsIgnored() throws Exception {
        token(12, "READER"); token(13, "READER");
        for (long reader : new long[]{12, 13}) {
            long id = reader + 100;
            when(loans.findHeaderForReader(id, reader)).thenReturn(Optional.of(new LoanDetailResponse(
                    id, "PM-" + id, null, reader, "Reader " + reader, 3L, "Staff", AT, List.of())));
            when(loans.findItemsForStaff(id)).thenReturn(List.of(new LoanDetailResponse.Item(
                    id + 200, id + 300, "BC-" + reader, 7L, "Sách của " + reader, AT, AT.plusDays(14))));
            mvc.perform(get("/api/v1/loans/" + id).param("readerId", "999").param("actorId", "999")
                            .header("Authorization", "Bearer token-" + reader))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.readerId").value(reader))
                    .andExpect(jsonPath("$.items[0].barcode").value("BC-" + reader));
        }
        mvc.perform(get("/api/v1/loans/113").param("readerId", "13").header("Authorization", "Bearer token-12"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.details").isEmpty());
        verify(loans).findHeaderForReader(113L, 12L);
        verify(loans, never()).findHeaderForStaff(anyLong());
    }
    @Test void foreignAndMissingResponseNeverContainsLoanFields() throws Exception {
        token(12, "READER");
        for (long id : new long[]{113, 999999}) {
            mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer token-12"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Phiếu không tồn tại hoặc bạn không có quyền truy cập."))
                    .andExpect(jsonPath("$.details").isEmpty()).andExpect(jsonPath("$.loanNumber").doesNotExist())
                    .andExpect(jsonPath("$.items").doesNotExist()).andExpect(jsonPath("$.readerId").doesNotExist());
        }
        verify(loans, never()).findItemsForStaff(anyLong());
    }
    @Test void selfListsUseJwtOnAllPagesAndStaffListRemainsForbiddenToReaders() throws Exception {
        token(12, "READER");
        when(loans.findUnreturnedForReader(12L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/loans/me/borrowed-books").param("readerId", "13").header("Authorization", "Bearer token-12"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        when(loans.countReturnedForReader(12L)).thenReturn(21L);
        when(loans.findReturnedForReader(eq(12L), eq(20), anyLong())).thenReturn(List.of());
        for (int page : new int[]{0, 1}) {
            mvc.perform(get("/api/v1/loans/me/returned-books").param("page", String.valueOf(page)).param("readerId", "13")
                            .header("Authorization", "Bearer token-12"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
            verify(loans).findReturnedForReader(12L, 20, (long) page * 20);
        }
        mvc.perform(get("/api/v1/loans").header("Authorization", "Bearer token-12")).andExpect(status().isForbidden());
        verify(loans, never()).findAllForStaff();
    }
    @Test void anonymousInvalidInactiveAndRevokedTokensCannotReadDetails() throws Exception {
        mvc.perform(get("/api/v1/loans/112")).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("bad"));
        mvc.perform(get("/api/v1/loans/112").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        User reader = token(12, "READER"); reader.setStatus("LOCKED");
        mvc.perform(get("/api/v1/loans/112").header("Authorization", "Bearer token-12")).andExpect(status().isUnauthorized());
        reader.setStatus("ACTIVE"); reader.setTokenVersion(1);
        mvc.perform(get("/api/v1/loans/112").header("Authorization", "Bearer token-12")).andExpect(status().isUnauthorized());
        verifyNoInteractions(loans);
    }
    @Test void staffDetailAndInvalidIdsRetainExistingBehavior() throws Exception {
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(3, role);
            when(loans.findHeaderForStaff(112L)).thenReturn(Optional.of(new LoanDetailResponse(
                    112L, "PM-112", null, 12L, "Reader", 3L, "Staff", AT, List.of())));
            when(loans.findItemsForStaff(112L)).thenReturn(List.of());
            mvc.perform(get("/api/v1/loans/112").header("Authorization", "Bearer token-3")).andExpect(status().isOk());
        }
        token(12, "READER");
        for (String id : new String[]{"0", "-1", "abc", "9223372036854775808"}) {
            mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer token-12")).andExpect(status().isBadRequest());
        }
        verify(loans, never()).findHeaderForReader(anyLong(), anyLong());
    }
}
