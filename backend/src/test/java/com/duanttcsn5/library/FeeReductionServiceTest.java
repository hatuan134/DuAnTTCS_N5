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
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeeReductionServiceTest {
    private final OverdueFeeRepository fees = mock(OverdueFeeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FeeReductionService service = new FeeReductionService(fees, users);
    private final OffsetDateTime at = OffsetDateTime.parse("2026-10-12T10:00:00+07:00");
    private final OverdueFeeRepository.FeeRow fee = row(15000, 0);
    private User actor;

    private OverdueFeeRepository.FeeRow row(long original, long paid) {
        return new OverdueFeeRepository.FeeRow(5L, 8L, "PM-008", "Bạn đọc Bình", "Mắt biếc",
                "LIB-001", original, paid, at);
    }
    private FeeReductionRequestResponse response(long amount) {
        return new FeeReductionRequestResponse(2L, 5L, 8L, "PM-008", "Bạn đọc Bình", "Mắt biếc",
                "LIB-001", 12L, "Thủ thư An", 15000, amount, 15000,
                "Bạn đọc có hoàn cảnh khó khăn", at, "PENDING");
    }
    @BeforeEach void setup() {
        actor = new User(); actor.setId(12L); actor.setFullName("Thủ thư An"); actor.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARIAN"); actor.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(actor));
        when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(fee));
        when(fees.findOverdueFee(5L, false)).thenReturn(Optional.of(fee));
        when(fees.pendingReduction(5L)).thenReturn(Optional.empty());
    }
    private void code(Runnable call, String expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
    }
    private CreateFeeReductionRequest request(String amount, String reason) {
        return new CreateFeeReductionRequest(amount == null ? null : new BigDecimal(amount), reason);
    }

    @Test void partialRequestUsesAuthenticatedActorTrimsReasonAndKeepsOriginalFee() {
        when(fees.insertReduction(fee, 12L, "Thủ thư An", 10000L, "Bạn đọc có hoàn cảnh khó khăn"))
                .thenReturn(response(10000));
        var saved = service.create(5L, request("10000", "  Bạn đọc có hoàn cảnh khó khăn  "), 12L);
        assertThat(saved.status()).isEqualTo("PENDING");
        assertThat(saved.originalAmountVnd()).isEqualTo(15000);
        assertThat(saved.outstandingAmountVnd()).isEqualTo(15000);
        assertThat(saved.requestedAmountVnd()).isEqualTo(10000);
        assertThat(fee.paidAmountVnd()).isZero();
        var order = inOrder(fees);
        order.verify(fees).findOverdueFee(5L, true);
        order.verify(fees).pendingReduction(5L);
        order.verify(fees).insertReduction(fee, 12L, "Thủ thư An", 10000L, "Bạn đọc có hoàn cảnh khó khăn");
        verifyNoMoreInteractions(fees);
    }
    @Test void fullRequestAndOneVndRequestAreAllowedWithoutRounding() {
        for (long amount : new long[]{15000, 1}) {
            when(fees.insertReduction(fee, 12L, "Thủ thư An", amount, "Lý do"))
                    .thenReturn(response(amount));
            assertThat(service.create(5L, request(Long.toString(amount), "Lý do"), 12L).requestedAmountVnd())
                    .isEqualTo(amount);
        }
    }
    @Test void emptyWhitespaceNullAndOversizedReasonsNeverInsert() {
        for (String reason : new String[]{null, "", " ", "\t\n\r  ", "\u00A0\u202F\uFEFF", "x".repeat(1001)}) {
            code(() -> service.create(5L, request("10000", reason), 12L), "INVALID_REDUCTION_REASON");
        }
        code(() -> service.create(5L, null, 12L), "INVALID_REDUCTION_REASON");
        verify(fees, never()).insertReduction(any(), any(), any(), anyLong(), any());
    }
    @Test void nullZeroNegativeFractionExcessAndOverflowAmountsNeverInsert() {
        for (String amount : new String[]{null, "0", "-1", "10000.5", "15001", "999999999999999999999999999"}) {
            code(() -> service.create(5L, request(amount, "Lý do"), 12L), "INVALID_REDUCTION_AMOUNT");
        }
        verify(fees, never()).insertReduction(any(), any(), any(), anyLong(), any());
    }
    @Test void zeroFeeAndAnyCollectedMoneyBlockRequests() {
        for (var invalid : List.of(row(0, 0), row(15000, 1), row(15000, 15000))) {
            when(fees.findOverdueFee(5L, true)).thenReturn(Optional.of(invalid));
            code(() -> service.create(5L, request("10000", "Lý do"), 12L), "FEE_REDUCTION_NOT_ALLOWED");
        }
        verify(fees, never()).insertReduction(any(), any(), any(), anyLong(), any());
    }
    @Test void existingPendingRequestAndDatabaseDuplicateReturnConflict() {
        when(fees.pendingReduction(5L)).thenReturn(Optional.of(response(10000)));
        code(() -> service.create(5L, request("10000", "Lý do"), 12L), "FEE_REDUCTION_PENDING");
        verify(fees, never()).insertReduction(any(), any(), any(), anyLong(), any());
        when(fees.pendingReduction(5L)).thenReturn(Optional.empty());
        when(fees.insertReduction(fee, 12L, "Thủ thư An", 10000L, "Lý do"))
                .thenThrow(new DuplicateKeyException("pending"));
        code(() -> service.create(5L, request("10000", "Lý do"), 12L), "FEE_REDUCTION_PENDING");
    }
    @Test void nonLibrariansInactiveAndUnauthenticatedUsersCannotCreate() {
        for (String role : new String[]{"READER", "LIBRARY_MANAGER", "ADMIN"}) {
            actor.getRole().setCode(role);
            code(() -> service.create(5L, request("10000", "Lý do"), 12L), "FEE_REDUCTION_ROLE_REQUIRED");
        }
        actor.getRole().setCode("LIBRARIAN"); actor.setStatus("DISABLED");
        code(() -> service.create(5L, request("10000", "Lý do"), 12L), "FEE_REDUCTION_ROLE_REQUIRED");
        code(() -> service.create(5L, request("10000", "Lý do"), null), "LOGIN_REQUIRED");
        code(() -> service.create(5L, request("10000", "Lý do"), 99L), "UNAUTHORIZED");
        verifyNoInteractions(fees);
    }
    @Test void invalidAndMissingFeeIdsCannotCreate() {
        for (Long id : new Long[]{null, 0L, -1L}) code(
                () -> service.create(id, request("10000", "Lý do"), 12L), "INVALID_FEE_ID");
        when(fees.findOverdueFee(5L, true)).thenReturn(Optional.empty());
        code(() -> service.create(5L, request("10000", "Lý do"), 12L), "OVERDUE_FEE_NOT_FOUND");
        verify(fees, never()).insertReduction(any(), any(), any(), anyLong(), any());
    }
    @Test void detailShowsPendingRequestAndUnchangedReceivableForEveryStaffRole() {
        when(fees.pendingReduction(5L)).thenReturn(Optional.of(response(10000)));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            actor.getRole().setCode(role);
            var detail = service.detail(5L, 12L);
            assertThat(detail.originalAmountVnd()).isEqualTo(15000);
            assertThat(detail.outstandingAmountVnd()).isEqualTo(15000);
            assertThat(detail.pendingRequest().requestedAmountVnd()).isEqualTo(10000);
        }
    }
    @Test void readerCannotReadFeeDetailsAndOnlyManagerCanReadPendingList() {
        actor.getRole().setCode("READER");
        code(() -> service.detail(5L, 12L), "FEE_REDUCTION_ROLE_REQUIRED");
        for (String role : new String[]{"READER", "LIBRARIAN", "ADMIN"}) {
            actor.getRole().setCode(role);
            code(() -> service.pending(0, 12L), "FEE_REDUCTION_ROLE_REQUIRED");
        }
        verifyNoInteractions(fees);
        actor.getRole().setCode("LIBRARY_MANAGER");
        when(fees.countPendingReductions()).thenReturn(1L);
        when(fees.pendingReductions(20, 0L)).thenReturn(List.of(response(10000)));
        var page = service.pending(999, 12L);
        assertThat(page.page()).isZero(); assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        code(() -> service.pending(-1, 12L), "INVALID_PAGE");
    }
}
