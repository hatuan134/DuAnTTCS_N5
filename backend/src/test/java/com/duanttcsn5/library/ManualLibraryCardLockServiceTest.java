package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest;
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

class ManualLibraryCardLockServiceTest {
    LibraryCardRepository cards = mock(LibraryCardRepository.class);
    ReaderProfileRepository profiles = mock(ReaderProfileRepository.class);
    AuditLogService audit = mock(AuditLogService.class);
    LibraryCardService service = new LibraryCardService(profiles, cards, mock(CardTypeRepository.class),
            audit, mock(AuditLogRepository.class));
    LibraryCard card() {
        var user = new User(); user.setId(12L); user.setFullName("Nguyễn Văn A"); user.setStatus("ACTIVE");
        var type = new CardType(); type.setId(2L); type.setName("Thẻ sinh viên");
        var card = new LibraryCard(); card.setId(5L); card.setUser(user); card.setCardType(type);
        card.setCardNumber("LIB-A"); card.setExpiresAt(LocalDate.now().plusYears(1));
        return card;
    }
    void selected(LibraryCard c) {
        when(cards.findReaderId(5L)).thenReturn(Optional.of(12L));
        when(cards.findByIdForUpdate(5L)).thenReturn(Optional.of(c));
        when(cards.sumUnpaidFees(12L)).thenReturn(new BigDecimal("15000"));
    }
    @Test void timedLockIsImmediateAndPreservesBaseCardAndAccountStatus() {
        var c = card(); selected(c); var ends = OffsetDateTime.now().plusDays(7);
        var result = service.lockCard(5L, new LockLibraryCardRequest("  Vi phạm quy định thư viện  ", ends), 9L, "127.0.0.1");
        assertThat(result.status()).isEqualTo("LOCKED"); assertThat(result.manualLock().active()).isTrue();
        assertThat(result.manualLock().reason()).isEqualTo("Vi phạm quy định thư viện");
        assertThat(result.manualLock().actorUserId()).isEqualTo(9L);
        assertThat(result.unpaidFees()).isEqualByComparingTo("15000");
        assertThat(c.getStatus()).isEqualTo("ACTIVE"); assertThat(c.getUser().getStatus()).isEqualTo("ACTIVE");
        assertThat(c.hasActiveManualLock(ends.minusSeconds(1))).isTrue();
        assertThat(c.hasActiveManualLock(ends)).isFalse();
        assertThat(LibraryCardService.resolveCardStatus(c, ends.toLocalDate(), ends)).isEqualTo("ACTIVE");
        var order = inOrder(cards, audit);
        order.verify(cards).findReaderId(5L); order.verify(cards).lockReaderForTransactions(12L);
        order.verify(cards).findByIdForUpdate(5L); order.verify(cards).saveAndFlush(c);
        order.verify(audit).logLibraryCardLocked(9L, c, "127.0.0.1");
    }
    @Test void indefiniteLockNeverExpiresAndDoesNotConsumeLoansOrReservations() {
        var c = card(); selected(c);
        service.lockCard(5L, new LockLibraryCardRequest("Vi phạm", null), 9L, "127.0.0.1");
        assertThat(c.hasActiveManualLock(OffsetDateTime.now().plusYears(100))).isTrue();
        assertThat(c.getManualLockEndsAt()).isNull();
        assertThat(c.manualLockMessage(OffsetDateTime.now())).contains("Vi phạm", "Khóa đến khi mở thủ công");
    }
    @Test void reasonsMustNotBeBlankOrTooLong() {
        for (String reason : new String[]{"", "   ", "\n\t", "x".repeat(1001)}) {
            assertThatThrownBy(() -> service.lockCard(5L, new LockLibraryCardRequest(reason, null), 9L, null))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_LOCK_REASON"));
        }
        assertThatThrownBy(() -> service.lockCard(5L, new LockLibraryCardRequest(null, null), 9L, null))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(cards, audit);
    }
    @Test void pastOrPresentExpiryCannotWriteOrAudit() {
        var c = card(); selected(c);
        for (var end : List.of(OffsetDateTime.now().minusDays(1), OffsetDateTime.now())) {
            assertThatThrownBy(() -> service.lockCard(5L, new LockLibraryCardRequest("Vi phạm", end), 9L, null))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_LOCK_EXPIRY"));
        }
        verify(cards, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
    @Test void activeLockCannotBeReplacedAndExpiredOneCan() {
        var c = card(); selected(c); var now = OffsetDateTime.now();
        c.applyManualLock("Lý do cũ", now.minusDays(1), now.plusDays(1), 8L);
        assertThatThrownBy(() -> service.lockCard(5L, new LockLibraryCardRequest("Lý do mới", null), 9L, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LIBRARY_CARD_ALREADY_LOCKED"));
        assertThat(c.getManualLockReason()).isEqualTo("Lý do cũ"); verifyNoInteractions(audit);
        c.applyManualLock("Lý do cũ", now.minusDays(2), now.minusDays(1), 8L);
        service.lockCard(5L, new LockLibraryCardRequest("Lý do mới", null), 9L, null);
        assertThat(c.getManualLockReason()).isEqualTo("Lý do mới"); verify(audit).logLibraryCardLocked(9L, c, null);
    }
    @Test void expiredManualLockDoesNotReactivateExpiredOrDisabledCard() {
        var c = card(); var now = OffsetDateTime.now();
        c.applyManualLock("Vi phạm", now.minusDays(2), now.minusDays(1), 9L);
        c.setExpiresAt(now.toLocalDate().minusDays(1));
        assertThat(LibraryCardService.resolveCardStatus(c, now.toLocalDate(), now)).isEqualTo("EXPIRED");
        c.setStatus("DISABLED");
        assertThat(LibraryCardService.resolveCardStatus(c, now.toLocalDate(), now)).isEqualTo("DISABLED");
    }
    @Test void readerCardResponseContainsLockReasonAndDuration() {
        var c = card(); var p = new ReaderProfile(); p.setUserId(12L); p.setUser(c.getUser()); p.setMemberCode("BD12");
        c.applyManualLock("Vi phạm", OffsetDateTime.now().minusHours(1), null, 9L);
        when(profiles.findById(12L)).thenReturn(Optional.of(p));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(c));
        var result = service.getMyCard(12L);
        assertThat(result.cardStatus()).isEqualTo("LOCKED"); assertThat(result.manualLock().reason()).isEqualTo("Vi phạm");
        assertThat(result.manualLock().endsAt()).isNull();
    }
    @Test void lockedReaderCannotRenewAndNoDueDateOrCounterIsWritten() {
        var c = card(); var now = OffsetDateTime.parse("2026-10-14T10:00:00+07:00");
        c.applyManualLock("Vi phạm", now.minusDays(1), now.plusDays(7), 9L);
        var users = mock(UserRepository.class); var loans = mock(LoanRepository.class);
        var reservations = mock(BookReservationRepository.class);
        var role = new Role(); role.setCode("READER"); c.getUser().setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(c.getUser()));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(c));
        var loanService = new LoanService(mock(BookRepository.class), reservations, mock(BookCopyRepository.class),
                cards, users, loans, mock(LibraryConfigurationService.class), Clock.fixed(now.toInstant(), ZoneOffset.UTC));
        assertThatThrownBy(() -> loanService.checkMyLoanRenewal(100L, 12L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LIBRARY_CARD_LOCKED"); assertThat(e.getMessage()).contains("Vi phạm");
                });
        verify(reservations).lockReaderForCreation(12L); verifyNoInteractions(loans);
    }
}
