package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReaderRenewalCheckControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({ SecurityConfig.class, LoanController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class })
    static class Config {
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean LoanRepository loans() { return mock(LoanRepository.class); }
        @Bean BookReservationRepository reservations() { return mock(BookReservationRepository.class); }
        @Bean LoanService service(UserRepository users, LoanRepository loans, BookReservationRepository reservations) {
            return new LoanService(mock(BookRepository.class), reservations,
                    mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans,
                    RenewalCalendarStub.mockCalendar(), Clock.fixed(
                            OffsetDateTime.parse("2026-10-08T09:00:00Z").toInstant(), ZoneOffset.UTC));
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private LoanRepository repository;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        repository = context.getBean(LoanRepository.class);
        when(repository.findRenewalPolicyForReader(100L, 12L)).thenReturn(
                Optional.of(new LoanRepository.RenewalPolicy(50L, 0, 2, 7)));
        when(repository.incrementRenewalCountIfAllowed(50L, 12L)).thenReturn(1);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    private void token(long id, String roleName) {
        String value = "token-" + id;
        Jwt jwt = Jwt.withTokenValue(value).header("alg", "HS256").subject(Long.toString(id))
                .claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode(value)).thenReturn(jwt);
        User user = new User(); user.setId(id); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleName); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(user));
    }

    @Test void readerCanCheckOnlyTheirOwnOpenLoanItem() throws Exception {
        token(12, "READER");
        when(repository.findRenewalCandidateForReader(100L, 12L)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-09T01:00:00Z"), null)));
        mvc.perform(post("/api/v1/loans/me/borrowed-books/100/renewal-check")
                .header("Authorization", "Bearer token-12").param("readerId", "13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.renewalsUsed").value(1))
                .andExpect(jsonPath("$.maxRenewals").value(2))
                .andExpect(jsonPath("$.dueAt").value("2026-10-16T17:00:00+07:00"));
        verify(repository).findRenewalCandidateForReader(100L, 12L);
        verify(repository, never()).findRenewalCandidateForReader(100L, 13L);
    }

    @Test void anotherReaderWaitingReturnsConflictAndReason() throws Exception {
        token(12, "READER");
        when(repository.findRenewalCandidateForReader(100L, 12L)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-09T01:00:00Z"), null)));
        when(context.getBean(BookReservationRepository.class)
                .existsOtherEffectiveReservationForLoanItem(eq(100L), eq(12L), any())).thenReturn(true);
        mvc.perform(post("/api/v1/loans/me/borrowed-books/100/renewal-check")
                .header("Authorization", "Bearer token-12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENEWAL_BLOCKED_BY_RESERVATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Bạn đọc khác")));
    }

    @Test void overdueOtherLoanAndUnpaidFeesGiveBothReasonsInOneConflict() throws Exception {
        token(12, "READER");
        when(repository.findRenewalCandidateForReader(100L, 12L)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-09T01:00:00Z"), null)));
        when(repository.countOtherOverdueUnreturnedLoansForReader(12L, 50L,
                java.time.LocalDate.of(2026, 10, 8))).thenReturn(2L);
        when(repository.sumUnpaidFeesForReader(12L)).thenReturn(new java.math.BigDecimal("15000"));
        mvc.perform(post("/api/v1/loans/me/borrowed-books/100/renewal-check")
                .header("Authorization", "Bearer token-12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENEWAL_BLOCKED_BY_VIOLATIONS"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("2 phiếu mượn khác quá hạn"),
                                org.hamcrest.Matchers.containsString("nợ phí"),
                                org.hamcrest.Matchers.containsString("15.000"))));
        verify(repository, never()).incrementRenewalCountIfAllowed(anyLong(), anyLong());
    }

    @Test void foreignOrMissingItemIs404WithoutLeakingItsState() throws Exception {
        token(12, "READER");
        mvc.perform(post("/api/v1/loans/me/borrowed-books/101/renewal-check")
                .header("Authorization", "Bearer token-12"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test void returnedAndOverdueItemUseConflictResponse() throws Exception {
        token(12, "READER");
        when(repository.findRenewalCandidateForReader(101L, 12L)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-09T00:00:00Z"),
                        OffsetDateTime.parse("2026-10-08T08:00:00Z"))));
        mvc.perform(post("/api/v1/loans/me/borrowed-books/101/renewal-check")
                .header("Authorization", "Bearer token-12"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));
        when(repository.findRenewalCandidateForReader(101L, 12L)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-07T12:00:00Z"), null)));
        mvc.perform(post("/api/v1/loans/me/borrowed-books/101/renewal-check")
                .header("Authorization", "Bearer token-12"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOAN_OVERDUE"));
    }

    @Test void staffAndAnonymousCannotUseReaderEndpoint() throws Exception {
        mvc.perform(post("/api/v1/loans/me/borrowed-books/100/renewal-check"))
                .andExpect(status().isUnauthorized());
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            long id = role.equals("LIBRARIAN") ? 20 : role.equals("LIBRARY_MANAGER") ? 21 : 22;
            token(id, role);
            mvc.perform(post("/api/v1/loans/me/borrowed-books/100/renewal-check")
                    .header("Authorization", "Bearer token-" + id))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(repository);
    }
}
