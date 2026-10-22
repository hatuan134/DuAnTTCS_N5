package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.dto.libraryconfig.PercentageOverduePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.bootstrap-admin.password=","app.scheduling.enabled=false",
        "app.permission-review.launch-enabled=false","app.pickup-email.automatic-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"})
@EnabledIfEnvironmentVariable(named="S4_09_2_DB_TEST",matches="true")
@ContextConfiguration(initializers=FullPermissionDatabaseTest.DatabaseGuard.class)
@Import(FullPermissionDatabaseTest.SafeTransport.class)
@Transactional
class PercentagePolicyDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OverdueFeeRepository fees;
    @Autowired LoanRepository loans;
    @Autowired LibraryConfigurationService configuration;
    Long user(String role) { return jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,'Policy test',?,'ACTIVE' FROM roles WHERE code=? RETURNING id",Long.class,UUID.randomUUID()+"@example.invalid",role); }
    Long copy(long price) {
        Long book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES ('Policy test',(SELECT min(id) FROM authors),(SELECT min(id) FROM categories)) RETURNING id",Long.class);
        return jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT min(id) FROM shelves),'AVAILABLE',CURRENT_DATE,?,'GOOD') RETURNING id",Long.class,book,"POL-"+UUID.randomUUID(),price);
    }
    Long item(Long loan,Long copy,OffsetDateTime due) {
        loans.insertItem(loan,copy,due.minusDays(7),due);
        return jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=? AND book_copy_id=?",Long.class,loan,copy);
    }
    @Test void handoverCapturesPriceAndPolicyAndConfirmedFeeRemainsImmutable() {
        Long admin=user("ADMIN"), reader=user("READER");
        var due=OffsetDateTime.now().minusDays(3); var at=due.plusDays(3);
        jdbc.update("UPDATE library_weekly_schedule SET is_open=true,open_time='08:00',close_time='18:00'");
        jdbc.update("DELETE FROM library_closed_dates WHERE closed_date BETWEEN ? AND ?",due.toLocalDate(),at.toLocalDate());
        Long loan=loans.insert(null,reader,admin,"POL-"+UUID.randomUUID(),due.minusDays(7));
        Long legacy=item(loan,copy(100000),due);
        assertTrue(fees.percentageSnapshot(legacy).isEmpty());
        fees.addPercentagePolicy(new PercentageOverduePolicy.Request(new BigDecimal("1"),new BigDecimal("1"),null),admin);
        Long copy=copy(50000), item=item(loan,copy,due);
        assertEquals(new BigDecimal("50000.00"),fees.percentageSnapshot(item).orElseThrow().coverPrice());
        jdbc.update("UPDATE book_copies SET cover_price=900000 WHERE id=?",copy);
        fees.addPercentagePolicy(new PercentageOverduePolicy.Request(new BigDecimal("2"),null,null),admin);
        var assessment=configuration.previewItemReturnFee(item,due,at);
        assertEquals(500L,assessment.amountVnd());assertEquals(new BigDecimal("1.0000"),assessment.dailyPercent());
        var saved=fees.record(item,reader,assessment);
        var persisted=fees.findForLoan(loan).get(item);
        assertEquals(saved.amountVnd(),persisted.amountVnd());
        assertEquals(saved.percentagePolicyId(),persisted.percentagePolicyId());
        assertEquals(saved.coverPrice(),persisted.coverPrice());
        assertEquals(saved.dailyPercent(),persisted.dailyPercent());
        assertEquals(saved.returnedAt().toInstant(),persisted.returnedAt().toInstant());
        assertTrue(fees.percentageSnapshot(legacy).isEmpty());
        Long next=item(loan,copy(100000),due);
        assertEquals(6000L,configuration.previewItemReturnFee(next,due,at).amountVnd());
    }
    @Test void activePolicyRejectsZeroPriceAndRollsBackCopyHandover() {
        Long admin=user("ADMIN"),reader=user("READER");
        fees.addPercentagePolicy(new PercentageOverduePolicy.Request(new BigDecimal("1"),null,null),admin);
        Long loan=loans.insert(null,reader,admin,"POL-"+UUID.randomUUID(),OffsetDateTime.now());
        Long copy=copy(0);
        assertThrows(Exception.class,()->item(loan,copy,OffsetDateTime.now().plusDays(7)));
    }
    @Test void scheduledPolicyDoesNotReplaceCurrentPolicyUntilEffective() {
        Long admin=user("ADMIN");
        assertNull(fees.currentPercentagePolicy());
        fees.addPercentagePolicy(new PercentageOverduePolicy.Request(new BigDecimal("1"),null,null),admin);
        Long active=fees.currentPercentagePolicy().id();
        fees.addPercentagePolicy(new PercentageOverduePolicy.Request(new BigDecimal("2"),new BigDecimal("50"),OffsetDateTime.now().plusDays(1)),admin);
        assertEquals(active,fees.currentPercentagePolicy().id());
        assertEquals(new BigDecimal("2.0000"),fees.percentagePolicies(0).get(0).dailyPercent());
        assertEquals(active,fees.percentagePolicies(0).get(0).previousPolicyId());
    }
}
