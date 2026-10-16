package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.LoanRejectionRepository;
import com.duanttcsn5.library.dto.loan.LoanRejectionResponse;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Run only against a disposable PostgreSQL database, as with the existing DB suites. */
@SpringBootTest(properties = {"app.bootstrap-admin.password=", "app.pickup-email.automatic-enabled=false"})
@EnabledIfEnvironmentVariable(named = "S4_00_1_DB_TEST", matches = "true")
@Transactional
class SprintFourFixDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanRejectionRepository rejections;
    @Autowired LoanService service;
    private Long user(String role, String name, String phone) {
        return jdbc.queryForObject("""
            INSERT INTO users(role_id, full_name, email, phone, status)
            SELECT id, ?, ?, ?, 'ACTIVE' FROM roles WHERE code=? RETURNING id
            """, Long.class, name, UUID.randomUUID()+"@example.invalid", phone, role);
    }
    @Test void namePhonePartialCodeAndBlankSearchPageByDistinctLoans() {
        String name="S4001 An "+UUID.randomUUID();
        Long reader=user("READER",name,"090 123 4567"), staff=user("ADMIN","Admin thử nghiệm",null);
        var time=OffsetDateTime.parse("2026-10-01T08:00:00+07:00");
        List<Long> ids=new ArrayList<>();
        for(int i=0;i<21;i++) ids.add(loans.insert(null,reader,staff,loans.nextLoanNumber(),time.plusSeconds(i)));
        var first=service.searchLoans(name,0,staff);
        assertThat(first.size()).isEqualTo(10); assertThat(first.total()).isEqualTo(21);
        assertThat(first.items()).hasSize(10);
        assertThat(first.items().get(0).readerPhone()).isEqualTo("090 123 4567");
        assertThat(first.items().get(0).readerId()).isEqualTo(reader);
        Set<Long> found=new HashSet<>();
        for(int page=0;page<3;page++) service.searchLoans(name,page,staff).items().forEach(item->assertThat(found.add(item.id())).isTrue());
        assertThat(found).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(service.searchLoans("0901234567",0,staff).total()).isGreaterThanOrEqualTo(21);
        assertThat(service.searchLoans("",0,staff).items()).hasSizeLessThanOrEqualTo(10);
        assertThat(service.searchLoans(name,0,staff,"2026-10-02",null,null).total()).isZero();
        String code=first.items().get(0).loanNumber();
        assertThat(service.searchLoans(code,0,staff).items()).extracting(item->item.loanNumber()).contains(code);
        assertThat(service.searchLoans("%_",0,staff).total()).isZero();
    }
    @Test void readerWithoutLoansIsSearchableAndReaderRoleCannotUseStaffLookup() {
        String name="S4001 New "+UUID.randomUUID();
        Long reader=user("READER",name,"0912345678"), staff=user("ADMIN","Admin",null);
        var page=service.searchReaders(name,0,staff);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().get(0).cardNumber()).isNull();
        assertThatThrownBy(()->service.searchReaders(name,0,reader)).hasMessageContaining("nhân viên");
        assertThatThrownBy(()->service.searchLoans(name,0,reader)).hasMessageContaining("nhân viên");
    }
    @Test void rejectionSearchMatchesSnapshotNameCurrentPhoneAndLiteralInput() {
        String name="S4001 Rejection "+UUID.randomUUID();
        Long reader=user("READER",name,"098 765 4321"), staff=user("ADMIN","Admin",null);
        rejections.save(UUID.randomUUID(),"CARD_CHECK",reader,name,"CARD-"+reader,staff,"Admin",null,0,3,0,
            java.math.BigDecimal.ZERO,List.of(new LoanRejectionResponse.Reason("TEST","Kiểm tra bộ lọc")));
        assertThat(rejections.page(name,0,10).total()).isEqualTo(1);
        assertThat(rejections.page("0987654321",0,10).total()).isGreaterThanOrEqualTo(1);
        assertThat(rejections.page("%_",0,10).total()).isZero();
    }
    @Test void concurrentNumberAllocationsAreUnique() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<String>> pending=new ArrayList<>();
            for(int i=0;i<40;i++) pending.add(pool.submit(loans::nextLoanNumber));
            Set<String> codes=new HashSet<>();
            for(var result:pending) assertThat(codes.add(result.get(10,java.util.concurrent.TimeUnit.SECONDS))).isTrue();
            assertThat(codes).hasSize(40);
        } finally { pool.shutdownNow(); }
    }
    @Test void sequenceNeverTruncatesAndExistingCodesRemainUnchanged() {
        String a=loans.nextLoanNumber(), b=loans.nextLoanNumber();
        assertThat(a).matches("PM-[0-9]{6,}");assertThat(b).isNotEqualTo(a);
        Long reader=user("READER","S4001 Legacy",null), staff=user("ADMIN","Admin",null);
        String legacy="PM-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        Long id=loans.insert(null,reader,staff,legacy,OffsetDateTime.now());
        assertThat(loans.findHeaderForStaff(id).orElseThrow().loanNumber()).isEqualTo(legacy);
        assertThat(service.searchLoans(legacy,0,staff).items()).extracting(item->item.id()).contains(id);
    }
}
