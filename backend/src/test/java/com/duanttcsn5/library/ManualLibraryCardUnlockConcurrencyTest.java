package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest;
import com.duanttcsn5.library.dto.librarycard.UnlockLibraryCardRequest;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.LibraryCardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Commits independent transactions: use only a disposable database. Deletes its own fixtures in finally. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_04_2_DB_TEST", matches = "true")
@WithMockUser(roles = "LIBRARY_MANAGER")
class ManualLibraryCardUnlockConcurrencyTest {
    @Autowired LibraryCardService cards;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    record Fixture(Long reader, Long manager, Long otherManager, Long card, Long type) {}
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id, full_name, email, status) SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code=? RETURNING id",
                Long.class, "S4042 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture() {
        return new TransactionTemplate(transactions).execute(tx -> {
            Long reader = user("READER"), manager = user("LIBRARY_MANAGER"), other = user("LIBRARY_MANAGER");
            Long type = jdbc.queryForObject("INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days) VALUES (?, 5, 14, 3, 7) RETURNING id", Long.class, "S4042-" + UUID.randomUUID());
            jdbc.update("INSERT INTO reader_profiles(user_id, member_code, date_of_birth, registration_status) VALUES (?, ?, '2006-09-22', 'APPROVED')", reader, "S4042-" + UUID.randomUUID());
            Long card = jdbc.queryForObject("INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE-1, CURRENT_DATE+365, 'ACTIVE') RETURNING id", Long.class, "S4042-" + UUID.randomUUID(), reader, type);
            return new Fixture(reader, manager, other, card, type);
        });
    }
    void cleanup(Fixture f) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("DELETE FROM library_card_lock_history WHERE card_id=?", f.card());
            jdbc.update("DELETE FROM audit_logs WHERE entity_type='LIBRARY_CARD' AND entity_id=?", f.card().toString());
            jdbc.update("DELETE FROM library_cards WHERE id=?", f.card());
            jdbc.update("DELETE FROM reader_profiles WHERE user_id=?", f.reader());
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", f.reader(), f.manager(), f.otherManager());
            jdbc.update("DELETE FROM card_types WHERE id=?", f.type());
        });
    }
    @Test void twoManagersProduceExactlyOneReleaseAndOneConflict() throws Exception {
        var f = fixture(); var pool = Executors.newFixedThreadPool(2);
        try {
            var lock = cards.lockCard(f.card(), new LockLibraryCardRequest("Vi phạm", null), f.manager(), "127.0.0.1");
            var request = new UnlockLibraryCardRequest("Đã xử lý", lock.manualLock().startedAt());
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            List<Future<String>> attempts = new ArrayList<>();
            for (Long actor : List.of(f.manager(), f.otherManager())) attempts.add(pool.submit(() -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_LIBRARY_MANAGER"))));
                SecurityContextHolder.setContext(context);
                try {
                    ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    try { cards.unlockCard(f.card(), request, actor, "127.0.0.1"); return "OK"; }
                    catch (ApiException e) { return e.getCode(); }
                } finally { SecurityContextHolder.clearContext(); }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<String> results = new ArrayList<>();
            for (var attempt : attempts) results.add(attempt.get(20, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("OK", "MANUAL_CARD_LOCK_NOT_ACTIVE");
            assertThat(cards.getCardDetails(f.card()).card().status()).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM library_card_lock_history WHERE card_id=? AND action='UNLOCK'", Long.class, f.card())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='LIBRARY_CARD_MANUALLY_UNLOCKED' AND entity_id=?", Long.class, f.card().toString())).isEqualTo(1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue(); cleanup(f); }
    }
    @Test void failedAuditRollsBackReleaseAndHistoryTogether() {
        var f = fixture();
        try {
            var lock = cards.lockCard(f.card(), new LockLibraryCardRequest("Vi phạm", null), f.manager(), "127.0.0.1");
            assertThatThrownBy(() -> cards.unlockCard(f.card(), new UnlockLibraryCardRequest("Đã xử lý", lock.manualLock().startedAt()),
                    f.manager(), "invalid-ip-address")).isInstanceOf(RuntimeException.class);
            assertThat(cards.getCardDetails(f.card()).card().manualLock().active()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM library_card_lock_history WHERE card_id=? AND action='UNLOCK'", Long.class, f.card())).isZero();
        } finally { cleanup(f); }
    }
}
