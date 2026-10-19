package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.OverdueFeeRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.FeeReductionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeeReductionDecisionServiceTest {
    final OverdueFeeRepository fees = mock(OverdueFeeRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final FeeReductionService service = new FeeReductionService(fees, users);
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-12T10:00:00+07:00");
    User actor;

    OverdueFeeRepository.FeeRow fee(long paid, long waived) {
        return new OverdueFeeRepository.FeeRow(5L, 8L, "PM-008", "Bạn đọc Bình", "Mắt biếc",
                "LIB-001", 15000, paid, at, waived);
    }
    FeeReductionRequestResponse request(String status, long amount) {
        return new FeeReductionRequestResponse(2L, 5L, 8L, "PM-008", "Bạn đọc Bình", "Mắt biếc",
                "LIB-001", 10L, "Thủ thư An", 15000, amount,
                "APPROVED".equals(status) ? 15000 - amount : 15000, "Hoàn cảnh khó khăn", at, status,
                0, "APPROVED".equals(status) ? amount : 0, "PENDING".equals(status) ? null : 12L,
                "PENDING".equals(status) ? null : "Quản lý Hạnh", "PENDING".equals(status) ? null : at,
                "REJECTED".equals(status) ? "Chưa đủ căn cứ" : null);
    }
    @BeforeEach void setup() {
        actor = new User(); actor.setId(12L); actor.setFullName("Quản lý Hạnh"); actor.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARY_MANAGER"); actor.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(actor));
        when(fees.reductionFeeId(2L)).thenReturn(Optional.of(5L));
        when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(fee(0, 0)));
        when(fees.findReduction(2L, true)).thenReturn(Optional.of(request("PENDING", 10000)));
        when(fees.decide(eq(2L), anyString(), eq(12L), any())).thenReturn(1);
    }
    void code(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
    @Test void approveUsesRequestedAmountAndReloadsCanonicalDecisionAfterFeeThenRequestLocks() {
        when(fees.findReduction(2L, false)).thenReturn(Optional.of(request("APPROVED", 10000)));
        var result = service.approve(2L, 12L);
        assertThat(result.approvedAmountVnd()).isEqualTo(10000);
        assertThat(result.outstandingAmountVnd()).isEqualTo(5000);
        assertThat(result.decidedBy()).isEqualTo(12L);
        var order = inOrder(fees);
        order.verify(fees).reductionFeeId(2L);
        order.verify(fees).findOverdueFee(5L, true);
        order.verify(fees).findReduction(2L, true);
        order.verify(fees).decide(2L, "APPROVED", 12L, null);
        order.verify(fees).findReduction(2L, false);
    }
    @Test void adminCanApproveAndRejectFeeReduction() {
        actor.getRole().setCode("ADMIN");
        when(fees.findReduction(2L, false)).thenReturn(Optional.of(request("APPROVED", 10000)));
        assertThat(service.approve(2L, 12L).status()).isEqualTo("APPROVED");
        verify(fees).decide(2L, "APPROVED", 12L, null);
        when(fees.findReduction(2L, false)).thenReturn(Optional.of(request("REJECTED", 10000)));
        assertThat(service.reject(2L, new RejectFeeReductionRequest("Lý do"), 12L).status()).isEqualTo("REJECTED");
        verify(fees).decide(2L, "REJECTED", 12L, "Lý do");
    }
    @Test void fullWaiverReturnsZeroAndKeepsOriginalAmount() {
        when(fees.findReduction(2L, true)).thenReturn(Optional.of(request("PENDING", 15000)));
        when(fees.findReduction(2L, false)).thenReturn(Optional.of(request("APPROVED", 15000)));
        var result = service.approve(2L, 12L);
        assertThat(result.originalAmountVnd()).isEqualTo(15000);
        assertThat(result.outstandingAmountVnd()).isZero();
    }
    @Test void rejectionTrimsReasonReturnsZeroWaiverAndOriginalReceivable() {
        when(fees.findReduction(2L, false)).thenReturn(Optional.of(request("REJECTED", 10000)));
        var result = service.reject(2L, new RejectFeeReductionRequest(" \u00a0Chưa đủ căn cứ\uFEFF "), 12L);
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.approvedAmountVnd()).isZero();
        assertThat(result.outstandingAmountVnd()).isEqualTo(15000);
        verify(fees).decide(2L, "REJECTED", 12L, "Chưa đủ căn cứ");
    }
    @Test void blankUnicodeNullAndOversizeRejectionReasonNeverWrite() {
        for (String reason : new String[]{null, "", " \t\n", "\u00A0\u202F\uFEFF", "x".repeat(1001)}) {
            code(() -> service.reject(2L, new RejectFeeReductionRequest(reason), 12L), "INVALID_REJECTION_REASON");
        }
        code(() -> service.reject(2L, null, 12L), "INVALID_REJECTION_REASON");
        verify(fees, never()).decide(any(), any(), any(), any());
    }
    @Test void librarianReaderAndInactiveManagerCannotDecide() {
        for (String role : new String[]{"LIBRARIAN", "READER"}) {
            actor.getRole().setCode(role);
            code(() -> service.approve(2L, 12L), "FEE_REDUCTION_ROLE_REQUIRED");
            code(() -> service.reject(2L, new RejectFeeReductionRequest("Lý do"), 12L), "FEE_REDUCTION_ROLE_REQUIRED");
        }
        for (String role : new String[]{"LIBRARY_MANAGER", "ADMIN"}) {
            actor.getRole().setCode(role); actor.setStatus("DISABLED");
            code(() -> service.approve(2L, 12L), "FEE_REDUCTION_ROLE_REQUIRED");
        }
        code(() -> service.approve(2L, null), "LOGIN_REQUIRED");
        verifyNoInteractions(fees);
    }
    @Test void allRepeatedDecisionCombinationsFailWithoutWrites() {
        for (String state : new String[]{"APPROVED", "REJECTED"}) {
            when(fees.findReduction(2L, true)).thenReturn(Optional.of(request(state, 10000)));
            code(() -> service.approve(2L, 12L), "FEE_REDUCTION_PROCESSED");
            code(() -> service.reject(2L, new RejectFeeReductionRequest("Lý do"), 12L), "FEE_REDUCTION_PROCESSED");
        }
        verify(fees, never()).decide(any(), any(), any(), any());
    }
    @Test void anyPaymentBlocksApprovalButStillAllowsRejection() {
        when(fees.findReduction(2L, false)).thenReturn(Optional.of(request("REJECTED", 10000)));
        for (long paid : new long[]{1, 15000}) {
            when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(fee(paid, 0)));
            code(() -> service.approve(2L, 12L), "FEE_REDUCTION_PAID");
            assertThat(service.reject(2L, new RejectFeeReductionRequest("Đã thu tiền"), 12L).status()).isEqualTo("REJECTED");
        }
        verify(fees, never()).decide(2L, "APPROVED", 12L, null);
    }
    @Test void amountBeyondRemainingWaiverAndConditionalWriteFailureReturnConflict() {
        when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(fee(0, 10000)));
        code(() -> service.approve(2L, 12L), "FEE_REDUCTION_AMOUNT_CHANGED");
        when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(fee(0, 0)));
        when(fees.decide(2L, "APPROVED", 12L, null)).thenReturn(0);
        code(() -> service.approve(2L, 12L), "FEE_REDUCTION_PROCESSED");
    }
    @Test void invalidAndMissingRequestsFailBeforeLockingFees() {
        for (Long id : new Long[]{null, 0L, -1L}) code(() -> service.approve(id, 12L), "INVALID_REDUCTION_REQUEST_ID");
        code(() -> service.approve(999L, 12L), "FEE_REDUCTION_NOT_FOUND");
        verify(fees, never()).findOverdueFee(any(), anyBoolean());
    }
    @Test void librarianReadsHistoryAndReceivableAfterApprovalAndCannotOverRequestAgain() {
        actor.getRole().setCode("LIBRARIAN");
        when(fees.findOverdueFee(5L, false)).thenReturn(Optional.of(fee(0, 10000)));
        when(fees.pendingReduction(5L)).thenReturn(Optional.empty());
        when(fees.reductionsForFee(5L)).thenReturn(List.of(request("APPROVED", 10000)));
        var detail = service.detail(5L, 12L);
        assertThat(detail.waivedAmountVnd()).isEqualTo(10000);
        assertThat(detail.paidAmountVnd()).isZero();
        assertThat(detail.outstandingAmountVnd()).isEqualTo(5000);
        assertThat(detail.reductionRequests().get(0).deciderName()).isEqualTo("Quản lý Hạnh");
        when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(fee(0, 10000)));
        code(() -> service.create(5L, new CreateFeeReductionRequest(BigDecimal.valueOf(10000), "Lý do"), 12L), "INVALID_REDUCTION_AMOUNT");
    }
}
