package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.LoanRejectionResponse;
import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LoanRejectionLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanRejectionLogServiceTest {
    private LoanRejectionRepository repository;
    private LoanRejectionLogService service;

    @BeforeEach void setup() {
        repository = mock(LoanRejectionRepository.class);
        LoanRepository loans = mock(LoanRepository.class);
        UserRepository users = mock(UserRepository.class);
        User staff = new User(); staff.setId(3L); staff.setFullName("Thủ thư A");
        when(users.findById(3L)).thenReturn(Optional.of(staff));
        when(loans.countOverdueUnreturnedLoansForReader(eq(12L), any(LocalDate.class))).thenReturn(2L);
        when(loans.sumUnpaidFeesForReader(12L)).thenReturn(new BigDecimal("145000"));
        service = new LoanRejectionLogService(repository, loans, users);
    }

    @Test void rejectedCheckRecordsAllReasonsAndFinancialSnapshot() {
        var reasons = List.of(
                new ReaderLoanEligibilityResponse.BlockReason("LOAN_LIMIT_REACHED", "Đạt hạn mức 5/5"),
                new ReaderLoanEligibilityResponse.BlockReason("LIBRARY_CARD_EXPIRED", "Thẻ hết hạn"),
                new ReaderLoanEligibilityResponse.BlockReason("LIBRARY_CARD_LOCKED", "Thẻ bị khóa"),
                new ReaderLoanEligibilityResponse.BlockReason("LOAN_OVERDUE_UNRETURNED", "Quá hạn 2 phiếu"),
                new ReaderLoanEligibilityResponse.BlockReason("LOAN_UNPAID_FEES", "Nợ 145.000 ₫"));
        UUID requestId = UUID.randomUUID();
        var snapshot = new ReaderLoanEligibilityResponse(12L, "Nguyễn Văn An", "TV-0012", "Thẻ sinh viên",
                "LOCKED", LocalDate.of(2026, 9, 30), 5, 5, 0, false,
                "LOAN_LIMIT_REACHED", "Không được mượn", reasons);
        service.logEligibility(requestId, "CARD_CHECK", 3L, snapshot);
        verify(repository).save(eq(requestId), eq("CARD_CHECK"), eq(12L), eq("Nguyễn Văn An"),
                eq("TV-0012"), eq(3L), eq("Thủ thư A"), isNull(), eq(5L), eq(5),
                eq(2L), eq(new BigDecimal("145000")), argThat(saved ->
                        saved.size() == 5 && saved.stream().map(LoanRejectionResponse.Reason::code).toList()
                                .equals(reasons.stream().map(ReaderLoanEligibilityResponse.BlockReason::code).toList())));
    }

    @Test void eligibleCheckDoesNotWriteAudit() {
        var snapshot = new ReaderLoanEligibilityResponse(12L, "Nguyễn Văn An", "TV-0012", "Thẻ sinh viên",
                "ACTIVE", LocalDate.of(2026, 12, 30), 5, 0, 5, true,
                "ELIGIBLE", "Được mượn", List.of());
        service.logEligibility(UUID.randomUUID(), "CARD_CHECK", 3L, snapshot);
        verifyNoInteractions(repository);
    }

    @Test void nullRequestIdDoesNotCreateAmbiguousLog() {
        service.log(null, "DIRECT_CONFIRM", 12L, "Nguyễn Văn An", "TV-0012", 3L,
                null, 5, 5, List.of(new LoanRejectionResponse.Reason("LOAN_LIMIT_REACHED", "Đạt hạn mức")));
        verifyNoInteractions(repository);
    }
}
