package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.librarycard.UnlockLibraryCardRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualLibraryCardUnlockServiceTest {
    LibraryCardRepository cards = mock(LibraryCardRepository.class);
    AuditLogService audit = mock(AuditLogService.class);
    LibraryCardService service = new LibraryCardService(mock(ReaderProfileRepository.class), cards,
            mock(CardTypeRepository.class), audit, mock(AuditLogRepository.class));
    OffsetDateTime started = OffsetDateTime.now().minusDays(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    LibraryCard card(OffsetDateTime end) {
        var user = new User(); user.setId(12L); user.setFullName("Nguyễn Văn A"); user.setStatus("ACTIVE");
        var type = new CardType(); type.setId(2L); type.setName("Sinh viên");
        var c = new LibraryCard(); c.setId(5L); c.setUser(user); c.setCardType(type);
        c.setCardNumber("LIB-A"); c.setExpiresAt(LocalDate.now().plusYears(1));
        c.applyManualLock("Vi phạm quy định thư viện", started, end, 8L);
        when(cards.findReaderId(5L)).thenReturn(Optional.of(12L));
        when(cards.findByIdForUpdate(5L)).thenReturn(Optional.of(c));
        when(cards.sumUnpaidFees(12L)).thenReturn(new BigDecimal("15000"));
        return c;
    }
    UnlockLibraryCardRequest request() { return new UnlockLibraryCardRequest("  Đã xử lý vi phạm  ", started); }
    void rejects(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
    @Test void releasesTimedAndIndefiniteLocksWithoutWaivingDebtOrErasingLockMetadata() {
        for (var end : Arrays.asList(OffsetDateTime.now().plusDays(7), null)) {
            var c = card(end);
            var result = service.unlockCard(5L, request(), 9L, "127.0.0.1");
            assertThat(result.status()).isEqualTo("ACTIVE"); assertThat(result.manualLock().active()).isFalse();
            assertThat(c.getManualLockReleasedAt()).isAfterOrEqualTo(started);
            assertThat(c.getManualLockStartedAt()).isEqualTo(started);
            assertThat(c.getManualLockEndsAt()).isEqualTo(end);
            assertThat(c.getManualLockReason()).isEqualTo("Vi phạm quy định thư viện");
            assertThat(c.getUser().getStatus()).isEqualTo("ACTIVE");
            assertThat(result.unpaidFees()).isEqualByComparingTo("15000");
            verify(cards, atLeastOnce()).insertLockHistory(eq(5L), eq("LIB-A"), eq("UNLOCK"), eq(9L), any(),
                    eq("Đã xử lý vi phạm"), eq(started), eq(end), eq("Vi phạm quy định thư viện"), eq(8L), eq("LOCKED"), eq("ACTIVE"));
        }
    }
    @Test void rejectsBlankWhitespaceTooLongAndMissingReferenceWithoutAnyWrite() {
        for (var reason : new String[]{"", "   ", "\n\t", "\u00a0", "\u202f", "x".repeat(1001)}) {
            rejects("INVALID_UNLOCK_REASON", () -> service.unlockCard(5L, new UnlockLibraryCardRequest(reason, started), 9L, null));
        }
        rejects("INVALID_UNLOCK_REASON", () -> service.unlockCard(5L, null, 9L, null));
        rejects("LOCK_REFERENCE_REQUIRED", () -> service.unlockCard(5L, new UnlockLibraryCardRequest("Đã xử lý", null), 9L, null));
        verifyNoInteractions(cards, audit);
    }
    @Test void endedAbsentOrRepeatedLockCannotWriteAnotherEvent() {
        var c = card(OffsetDateTime.now().minusHours(1));
        rejects("MANUAL_CARD_LOCK_NOT_ACTIVE", () -> service.unlockCard(5L, request(), 9L, null));
        c.applyManualLock("Vi phạm", started, null, 8L);
        service.unlockCard(5L, request(), 9L, null);
        rejects("MANUAL_CARD_LOCK_NOT_ACTIVE", () -> service.unlockCard(5L, request(), 10L, null));
        verify(cards, times(1)).saveAndFlush(c);
        c.applyManualLock(null, null, null, null);
        rejects("MANUAL_CARD_LOCK_NOT_ACTIVE", () -> service.unlockCard(5L, request(), 9L, null));
    }
    @Test void staleConfirmationCannotUnlockANewerRestriction() {
        var c = card(null); c.applyManualLock("Khóa mới", started.plusHours(1), null, 8L);
        rejects("CARD_LOCK_CHANGED", () -> service.unlockCard(5L, request(), 9L, null));
        verify(cards, never()).saveAndFlush(any()); verifyNoInteractions(audit);
        assertThat(c.hasActiveManualLock(OffsetDateTime.now())).isTrue();
    }
    @Test void preservesOtherAccountRestrictionsExpiredDisabledAndLegacyCardLocks() {
        for (String restriction : List.of("ACCOUNT_LOCK", "TEMP_ACCOUNT_LOCK", "EXPIRED", "DISABLED", "CARD_LOCK")) {
            var c = card(null);
            switch (restriction) {
                case "ACCOUNT_LOCK" -> c.getUser().setStatus("LOCKED");
                case "TEMP_ACCOUNT_LOCK" -> c.getUser().setLockedUntil(OffsetDateTime.now().plusDays(1));
                case "EXPIRED" -> c.setExpiresAt(LocalDate.now().minusDays(1));
                case "DISABLED" -> c.setStatus("DISABLED");
                case "CARD_LOCK" -> c.setStatus("LOCKED");
            }
            var result = service.unlockCard(5L, request(), 9L, null);
            assertThat(result.manualLock().active()).isFalse();
            assertThat(result.status()).isEqualTo(switch (restriction) {
                case "EXPIRED" -> "EXPIRED"; case "DISABLED" -> "DISABLED"; default -> "LOCKED";
            });
        }
    }
    @Test void newLockAfterReleaseIsActiveAndDoesNotDeleteEarlierHistory() {
        var c = card(null); service.unlockCard(5L, request(), 9L, null);
        service.lockCard(5L, new com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest("Vi phạm mới", null), 10L, null);
        assertThat(c.getManualLockReleasedAt()).isNull(); assertThat(c.hasActiveManualLock(OffsetDateTime.now())).isTrue();
        verify(cards).insertLockHistory(eq(5L), any(), eq("UNLOCK"), eq(9L), any(), any(), any(), any(), any(), any(), any(), any());
        verify(cards).insertLockHistory(eq(5L), any(), eq("LOCK"), eq(10L), any(), any(), any(), any(), any(), any(), any(), any());
    }
    @Test void acquireReaderThenCardLockBeforeSavingAndAuditing() {
        var c = card(null); service.unlockCard(5L, request(), 9L, "127.0.0.1");
        var order = inOrder(cards, audit);
        order.verify(cards).findReaderId(5L); order.verify(cards).lockReaderForTransactions(12L);
        order.verify(cards).findByIdForUpdate(5L); order.verify(cards).saveAndFlush(c);
        order.verify(cards).insertLockHistory(eq(5L), any(), eq("UNLOCK"), eq(9L), any(), any(), any(), any(), any(), any(), any(), any());
        order.verify(audit).logLibraryCardUnlocked(eq(9L), eq(c), eq("Đã xử lý vi phạm"), eq("LOCKED"), eq("ACTIVE"), eq("127.0.0.1"));
    }
}
