package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.FeePaymentRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.FeePaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeePaymentServiceTest {
    FeePaymentRepository repository; UserRepository users; FeePaymentService service;
    final Reader reader = new Reader(7L, "Bạn đọc Bình", "THE-007", "BD-007", "binh@example.invalid", "0900000000", "ACTIVE");
    final Fee first = new Fee(1L, "RECORDED", "Khoản 1", 10000, 0, 0, 10000);
    final Fee second = new Fee(2L, "OVERDUE", "Khoản 2", 15000, 0, 0, 15000);
    final Fee third = new Fee(3L, "RECORDED", "Khoản 3", 5000, 0, 0, 5000);
    @BeforeEach void setup() {
        repository = mock(FeePaymentRepository.class); users = mock(UserRepository.class); service = new FeePaymentService(repository, users);
        Role role = new Role(); role.setCode("LIBRARIAN"); User user = new User(); user.setId(12L);
        user.setFullName("Thủ thư An"); user.setStatus("ACTIVE"); user.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(user));
        when(repository.reader(eq(7L), anyBoolean())).thenReturn(Optional.of(reader));
        when(repository.outstanding(eq(7L), anyBoolean())).thenReturn(List.of(first, second, third));
        when(repository.payments(7L)).thenReturn(List.of()); when(repository.replay(eq(7L), any())).thenReturn(Optional.empty());
    }
    CollectFullFeesRequest preview() {
        var d = service.detail(7L, 12L); return new CollectFullFeesRequest(UUID.randomUUID(), d.totalOutstandingVnd(), d.debtToken());
    }
    FeeReceiptResponse receipt() {
        return new FeeReceiptResponse(9L, "GD-9", "BL-9", 7L, reader.fullName(), reader.cardNumber(), 12L,
                "Thủ thư An", OffsetDateTime.now(), 30000, List.of());
    }
    void code(Runnable action, String expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(expected));
    }
    @Test void detailIncludesAllFeesAndSumEqualsComponents() {
        var d = service.detail(7L, 12L); assertThat(d.totalOutstandingVnd()).isEqualTo(30000);
        assertThat(d.fees()).containsExactly(first, second, third); assertThat(d.hasDebt()).isTrue();
        assertThat(d.reader().cardNumber()).isEqualTo("THE-007"); assertThat(d.debtToken()).hasSize(64);
    }
    @Test void oneFeeWithPriorPaymentAndWaiverCollectsOnlyRemainingAmount() {
        when(repository.outstanding(eq(7L), anyBoolean())).thenReturn(List.of(new Fee(1L, "RECORDED", "Cũ", 10000, 2000, 3000, 5000)));
        assertThat(service.detail(7L, 12L).totalOutstandingVnd()).isEqualTo(5000);
    }
    @Test void zeroDebtHasNoPaymentAndRejectsCollection() {
        when(repository.outstanding(eq(7L), anyBoolean())).thenReturn(List.of());
        var d = service.detail(7L, 12L); assertThat(d.hasDebt()).isFalse(); assertThat(d.totalOutstandingVnd()).isZero();
        code(() -> service.collect(7L, new CollectFullFeesRequest(UUID.randomUUID(), 1L, d.debtToken()), 12L), "NO_OUTSTANDING_FEES");
        verify(repository, never()).insert(any(), any(), any(), anyLong(), any(), any());
    }
    @Test void fullCollectionRecordsEveryAllocationAndChecksZeroBalance() {
        var input = preview(); when(repository.insert(reader, 12L, "Thủ thư An", 30000, input.requestKey(), input.debtToken())).thenReturn(9L);
        when(repository.settle(eq(7L), any())).thenReturn(1); when(repository.outstanding(7L, false)).thenReturn(List.of());
        when(repository.receipt(9L)).thenReturn(Optional.of(receipt()));
        assertThat(service.collect(7L, input, 12L).totalAmountVnd()).isEqualTo(30000);
        var order = inOrder(repository); order.verify(repository).reader(7L, false);
        order.verify(repository).outstanding(7L, false); order.verify(repository).payments(7L);
        order.verify(repository).reader(7L, true); order.verify(repository).replay(7L, input.requestKey());
        order.verify(repository).outstanding(7L, true);
        for (var fee : List.of(first, second, third)) { verify(repository).insertItem(9L, fee); verify(repository).settle(7L, fee); }
    }
    @Test void changedTotalCannotBeCollectedFromOldPreview() {
        var input = preview(); when(repository.outstanding(7L, true)).thenReturn(List.of(first));
        code(() -> service.collect(7L, input, 12L), "FEE_DEBT_CHANGED");
        verify(repository, never()).insert(any(), any(), any(), anyLong(), any(), any());
    }
    @Test void equalTotalWithDifferentComponentsAlsoRejectsStalePreview() {
        var input = preview(); when(repository.outstanding(7L, true)).thenReturn(List.of(new Fee(4L, "RECORDED", "Khác", 30000, 0, 0, 30000)));
        code(() -> service.collect(7L, input, 12L), "FEE_DEBT_CHANGED");
    }
    @Test void repeatedKeyReturnsCommittedReceiptBeforeCheckingDebt() {
        var input = preview(); var saved = receipt();
        when(repository.replay(7L, input.requestKey())).thenReturn(Optional.of(new FeePaymentRepository.Replay(9L, 12L, 30000, input.debtToken())));
        when(repository.receipt(9L)).thenReturn(Optional.of(saved));
        assertThat(service.collect(7L, input, 12L)).isEqualTo(saved);
        verify(repository, never()).outstanding(7L, true); verify(repository, never()).settle(any(), any());
    }
    @Test void reusedKeyWithDifferentActorOrAmountIsConflict() {
        var input = preview(); when(repository.replay(7L, input.requestKey())).thenReturn(Optional.of(new FeePaymentRepository.Replay(9L, 13L, 30000, input.debtToken())));
        code(() -> service.collect(7L, input, 12L), "PAYMENT_REQUEST_REUSED");
    }
    @Test void allOtherRolesAndInactiveActorsAreRejected() {
        for (String code : new String[]{"ADMIN", "LIBRARY_MANAGER", "READER"}) {
            users.findById(12L).orElseThrow().getRole().setCode(code);
            code(() -> service.detail(7L, 12L), "FEE_PAYMENT_ROLE_REQUIRED");
            code(() -> service.receipt(9L, 12L), "FEE_PAYMENT_ROLE_REQUIRED");
        }
        users.findById(12L).orElseThrow().getRole().setCode("LIBRARIAN"); users.findById(12L).orElseThrow().setStatus("DISABLED");
        code(() -> service.search("THE", 12L), "FEE_PAYMENT_ROLE_REQUIRED");
        code(() -> service.search("THE", null), "LOGIN_REQUIRED"); verifyNoInteractions(repository);
    }
    @Test void invalidSearchIdsAndRequestsFailBeforeWrites() {
        code(() -> service.search("  ", 12L), "INVALID_READER_SEARCH");
        code(() -> service.detail(-1L, 12L), "INVALID_ID");
        code(() -> service.collect(7L, null, 12L), "INVALID_PAYMENT_REQUEST");
        code(() -> service.collect(7L, new CollectFullFeesRequest(UUID.randomUUID(), 0L, "bad"), 12L), "INVALID_PAYMENT_REQUEST");
        verifyNoInteractions(repository);
    }
    @Test void allocationFailureIsVietnameseTransactionError() {
        var input = preview(); when(repository.insert(any(), any(), any(), anyLong(), any(), any())).thenReturn(9L);
        doThrow(new DataIntegrityViolationException("forced")).when(repository).insertItem(9L, first);
        code(() -> service.collect(7L, input, 12L), "FEE_PAYMENT_FAILED"); verify(repository, never()).settle(any(), any());
    }
    @Test void remainingDebtOrChangedRowAbortsPayment() {
        var input = preview(); when(repository.insert(any(), any(), any(), anyLong(), any(), any())).thenReturn(9L);
        when(repository.settle(eq(7L), any())).thenReturn(0);
        code(() -> service.collect(7L, input, 12L), "FEE_DEBT_CHANGED");
        when(repository.settle(eq(7L), any())).thenReturn(1);
        code(() -> service.collect(7L, input, 12L), "FEE_DEBT_CHANGED");
    }

    CollectPartialFeesRequest partial(long amount) {
        var d = service.detail(7L, 12L);
        return new CollectPartialFeesRequest(UUID.randomUUID(), d.totalOutstandingVnd(), d.debtToken(), amount);
    }
    FeeReceiptResponse stubPartial(CollectPartialFeesRequest input, List<Fee> remainingFees) {
        long amount = input.amountVnd().longValueExact(), after = input.expectedTotalVnd().longValueExact() - amount;
        var saved = new FeeReceiptResponse(9L, "GD-9", "BL-9", 7L, reader.fullName(), reader.cardNumber(), 12L,
                "Thủ thư An", OffsetDateTime.now(), amount, input.expectedTotalVnd().longValueExact(), after, "PARTIAL", List.of());
        when(repository.insert(reader, 12L, "Thủ thư An", amount, input.requestKey(), input.debtToken(), after, "PARTIAL")).thenReturn(9L);
        when(repository.applyPayment(eq(7L), any(), anyLong())).thenReturn(1);
        when(repository.outstanding(7L, false)).thenReturn(remainingFees);
        when(repository.receipt(9L)).thenReturn(Optional.of(saved));
        return saved;
    }
    @Test void partialCollectionPaysOldestFeeAndKeepsDebt() {
        var input = partial(4000);
        stubPartial(input, List.of(new Fee(1L, "RECORDED", "Khoản 1", 10000, 4000, 0, 6000), second, third));
        var saved = service.collectPartial(7L, input, 12L);
        assertThat(saved.totalAmountVnd()).isEqualTo(4000); assertThat(saved.debtAfterVnd()).isEqualTo(26000);
        verify(repository).insertItem(9L, first, 4000); verify(repository).applyPayment(7L, first, 4000);
        verify(repository, never()).applyPayment(eq(7L), eq(second), anyLong());
        assertThat(service.detail(7L, 12L).hasDebt()).isTrue();
    }
    @Test void partialCollectionSpansFeesInAscendingIdOrder() {
        var input = partial(15000);
        stubPartial(input, List.of(new Fee(2L, "OVERDUE", "Khoản 2", 15000, 5000, 0, 10000), third));
        service.collectPartial(7L, input, 12L);
        var order = inOrder(repository);
        order.verify(repository).insertItem(9L, first, 10000); order.verify(repository).applyPayment(7L, first, 10000);
        order.verify(repository).insertItem(9L, second, 5000); order.verify(repository).applyPayment(7L, second, 5000);
        verify(repository, never()).insertItem(eq(9L), eq(third), anyLong());
    }
    @Test void partialCollectionRespectsEarlierPaymentsAndWaivers() {
        var fee = new Fee(1L, "RECORDED", "Phí cũ", 10000, 2000, 3000, 5000);
        when(repository.outstanding(eq(7L), anyBoolean())).thenReturn(List.of(fee));
        var input = partial(1500);
        stubPartial(input, List.of(new Fee(1L, "RECORDED", "Phí cũ", 10000, 3500, 3000, 3500)));
        service.collectPartial(7L, input, 12L);
        verify(repository).insertItem(9L, fee, 1500); verify(repository).applyPayment(7L, fee, 1500);
        assertThat(service.detail(7L, 12L).totalOutstandingVnd()).isEqualTo(3500);
    }
    @Test void lastPartialCollectionMayEqualRemainingDebtAndRemoveDebt() {
        var input = partial(30000); stubPartial(input, List.of());
        var saved = service.collectPartial(7L, input, 12L);
        assertThat(saved.debtAfterVnd()).isZero(); assertThat(service.detail(7L, 12L).hasDebt()).isFalse();
        verify(repository).insertItem(9L, third, 5000);
    }
    @Test void invalidPartialAmountsFailBeforeLockingOrWriting() {
        var input = partial(1);
        for (String value : new String[]{"0", "-1", "1.5", "9007199254740992"}) {
            var invalid = new CollectPartialFeesRequest(input.requestKey(), input.expectedTotalVnd(), input.debtToken(), new java.math.BigDecimal(value));
            code(() -> service.collectPartial(7L, invalid, 12L), "INVALID_PAYMENT_AMOUNT");
        }
        code(() -> service.collectPartial(7L, new CollectPartialFeesRequest(input.requestKey(), input.expectedTotalVnd(), input.debtToken(), null), 12L), "INVALID_PAYMENT_AMOUNT");
        code(() -> service.collectPartial(7L, partial(30001), 12L), "PAYMENT_AMOUNT_EXCEEDS_DEBT");
        code(() -> service.collectPartial(7L, null, 12L), "INVALID_PAYMENT_REQUEST");
        verify(repository, never()).reader(7L, true);
    }
    @Test void repeatedPartialKeyReturnsOriginalReceiptEvenAfterDebtHasChanged() {
        var input = partial(10000); var saved = stubPartial(input, List.of(second, third));
        when(repository.replay(7L, input.requestKey())).thenReturn(Optional.of(
                new FeePaymentRepository.Replay(9L, 12L, 10000, input.debtToken(), 30000, "PARTIAL")));
        assertThat(service.collectPartial(7L, input, 12L)).isEqualTo(saved);
        verify(repository, never()).outstanding(7L, true); verify(repository, never()).applyPayment(any(), any(), anyLong());
    }
    @Test void partialKeyCannotBeReusedWithDifferentAmountTotalActorTokenOrMode() {
        var input = partial(10000);
        for (var replay : List.of(
                new FeePaymentRepository.Replay(9L, 12L, 9999, input.debtToken(), 30000, "PARTIAL"),
                new FeePaymentRepository.Replay(9L, 12L, 10000, input.debtToken(), 30001, "PARTIAL"),
                new FeePaymentRepository.Replay(9L, 13L, 10000, input.debtToken(), 30000, "PARTIAL"),
                new FeePaymentRepository.Replay(9L, 12L, 10000, "b".repeat(64), 30000, "PARTIAL"),
                new FeePaymentRepository.Replay(9L, 12L, 10000, input.debtToken(), 30000, "FULL"))) {
            when(repository.replay(7L, input.requestKey())).thenReturn(Optional.of(replay));
            code(() -> service.collectPartial(7L, input, 12L), "PAYMENT_REQUEST_REUSED");
        }
    }
    @Test void fullEndpointCannotReplayPartialPaymentEvenWhenItPaidLastBalance() {
        var input = preview();
        when(repository.replay(7L, input.requestKey())).thenReturn(Optional.of(
                new FeePaymentRepository.Replay(9L, 12L, 30000, input.debtToken(), 30000, "PARTIAL")));
        code(() -> service.collect(7L, input, 12L), "PAYMENT_REQUEST_REUSED");
    }
    @Test void partialStalePreviewRejectsChangedTotalOrComponentsBeforeWrites() {
        var input = partial(10000); when(repository.outstanding(7L, true)).thenReturn(List.of(first, second));
        code(() -> service.collectPartial(7L, input, 12L), "FEE_DEBT_CHANGED");
        when(repository.outstanding(7L, true)).thenReturn(List.of(new Fee(4L, "RECORDED", "Khác", 30000, 0, 0, 30000)));
        code(() -> service.collectPartial(7L, input, 12L), "FEE_DEBT_CHANGED");
        verify(repository, never()).applyPayment(any(), any(), anyLong());
    }
    @Test void partialFailureAndIncorrectBalanceAbortTheWholeCollection() {
        var input = partial(10000); stubPartial(input, List.of(second, third));
        when(repository.applyPayment(7L, first, 10000)).thenReturn(0);
        code(() -> service.collectPartial(7L, input, 12L), "FEE_DEBT_CHANGED");
        when(repository.applyPayment(7L, first, 10000)).thenReturn(1);
        when(repository.outstanding(7L, false)).thenReturn(List.of(third));
        code(() -> service.collectPartial(7L, input, 12L), "FEE_DEBT_CHANGED");
        doThrow(new DataIntegrityViolationException("forced")).when(repository).insertItem(9L, first, 10000);
        code(() -> service.collectPartial(7L, input, 12L), "FEE_PAYMENT_FAILED");
    }
    @Test void nonLibrariansCannotCollectPartialFees() {
        var input = partial(10000);
        for (String role : new String[]{"ADMIN", "LIBRARY_MANAGER", "READER"}) {
            users.findById(12L).orElseThrow().getRole().setCode(role);
            code(() -> service.collectPartial(7L, input, 12L), "FEE_PAYMENT_ROLE_REQUIRED");
        }
        verify(repository, never()).reader(7L, true);
    }
}
