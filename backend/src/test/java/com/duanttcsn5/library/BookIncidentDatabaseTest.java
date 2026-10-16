package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.incident.IncidentDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.BookIncidentService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Same opt-in disposable-DB convention as existing return/payment database tests. */
@SpringBootTest(properties="app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named="S4_03_1_DB_TEST",matches="true")
class BookIncidentDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookIncidentService service;
    @Autowired LoanRepository loans;
    @Autowired com.duanttcsn5.library.repository.BookCopyRepository copies;
    @Autowired LibraryConfigurationService configuration;
    @Autowired PlatformTransactionManager transactions;
    record Fixture(Long staff,Long other,Long manager,Long reader,Long loan,Long item,Long copy) {}
    @BeforeEach void configure() {
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=2,damaged_factor=1.5 WHERE id=1");
        jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd=5000 WHERE id=1");
        jdbc.update("UPDATE library_weekly_schedule SET is_open=true,open_time='08:00',close_time='17:00'");
        jdbc.update("DELETE FROM library_closed_dates WHERE closed_date BETWEEN CURRENT_DATE-20 AND CURRENT_DATE+1");
    }
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,?,?, 'ACTIVE' FROM roles WHERE code=? RETURNING id",
                Long.class,"S4031 "+role,UUID.randomUUID()+"@example.invalid",role);
    }
    Fixture fixture(boolean overdue) {
        Long staff=user("LIBRARIAN"),other=user("LIBRARIAN"),manager=user("LIBRARY_MANAGER"),reader=user("READER");
        Long book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT min(id) FROM authors),(SELECT min(id) FROM categories)) RETURNING id",
                Long.class,"S4031 "+UUID.randomUUID());
        Long copy=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT min(id) FROM shelves),'AVAILABLE',CURRENT_DATE-10,100000,'GOOD') RETURNING id",
                Long.class,book,"S4031-"+UUID.randomUUID());
        OffsetDateTime borrowed=OffsetDateTime.now().minusDays(8);
        Long loan=loans.insert(null,reader,staff,"S4031-"+UUID.randomUUID(),borrowed);
        loans.insertItem(loan,copy,borrowed,overdue?OffsetDateTime.now().minusDays(2):OffsetDateTime.now().plusDays(3));
        Long item=jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?",Long.class,loan);
        return new Fixture(staff,other,manager,reader,loan,item,copy);
    }
    Incident save(Fixture f,String type) {
        var p=service.preview(f.item(),type,f.staff());
        return service.confirm(f.item(),type,"Mô tả sự cố kiểm thử",p.token(),List.of(),f.staff());
    }
    void code(Runnable fn,String expected) {
        assertThatThrownBy(fn::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo(expected));
    }
    @Test void lostAndDamagedCreateDifferentFeesAndRemoveCopy() {
        for(String type:List.of("LOST","DAMAGED")) {
            var f=fixture(false);
            Long bookId=jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id=?",Long.class,f.copy());
            assertThat(copies.countStockByBookId(bookId)).isEqualTo(1);
            var result=save(f,type);
            assertThat(copies.countStockByBookId(bookId)).isZero();
            assertThat(copies.countAvailableByBookId(bookId)).isZero();
            assertThat(result.compensationVnd()).isEqualTo(type.equals("LOST")?200000:150000);
            assertThat(result.item().copyStatus()).isEqualTo("REMOVED"); assertThat(result.loanStatus()).isEqualTo(type);
            assertThat(result.overdueFee().amountVnd()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE id=? AND status='AVAILABLE'",Long.class,f.copy())).isZero();
            assertThat(jdbc.queryForObject("SELECT compensation_item_id FROM reader_fees WHERE id=?",Long.class,result.compensationFeeId())).isEqualTo(f.item());
            assertThatThrownBy(()->loans.insertItem(loans.insert(null,f.reader(),f.staff(),"S4031-"+UUID.randomUUID(),OffsetDateTime.now()),f.copy(),OffsetDateTime.now(),OffsetDateTime.now().plusDays(1))).isInstanceOf(Exception.class);
            assertThatThrownBy(()->jdbc.update("UPDATE book_copies SET status='AVAILABLE' WHERE id=?",f.copy())).isInstanceOf(Exception.class);
        }
    }
    @Test void requiredDescriptionMissingPriceAndFactorsAndUnknownClosedItems() {
        var f=fixture(false); var p=service.preview(f.item(),"LOST",f.staff());
        code(()->service.confirm(f.item(),"LOST"," ",p.token(),List.of(),f.staff()),"INVALID_INCIDENT_DESCRIPTION");
        code(()->service.preview(Long.MAX_VALUE,"LOST",f.staff()),"LOAN_ITEM_NOT_FOUND");
        jdbc.update("UPDATE book_copies SET cover_price=NULL WHERE id=?",f.copy());
        code(()->service.preview(f.item(),"LOST",f.staff()),"MISSING_COVER_PRICE");
        jdbc.update("UPDATE book_copies SET cover_price=100000 WHERE id=?",f.copy());
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=NULL WHERE id=1");
        code(()->service.preview(f.item(),"LOST",f.staff()),"FACTOR_NOT_CONFIGURED");
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=2 WHERE id=1"); save(f,"LOST");
        code(()->service.preview(f.item(),"LOST",f.other()),"ITEM_ALREADY_CLOSED");
    }
    @Test void changedFactorRequiresNewReviewAndPermissionsAreEnforced() {
        var f=fixture(false); var p=service.preview(f.item(),"LOST",f.staff());
        code(()->service.updateFactors(new Factors(BigDecimal.ONE,BigDecimal.ONE,null),f.staff()),"FORBIDDEN");
        code(()->service.preview(f.item(),"LOST",f.manager()),"FORBIDDEN");
        code(()->service.preview(f.item(),"LOST",f.reader()),"FORBIDDEN");
        service.updateFactors(new Factors(new BigDecimal("3"),new BigDecimal("1.5"),null),f.manager());
        code(()->service.confirm(f.item(),"LOST","Mô tả",p.token(),List.of(),f.staff()),"INCIDENT_PREVIEW_CHANGED");
        assertThat(jdbc.queryForObject("SELECT returned_at IS NULL FROM loan_items WHERE id=?",Boolean.class,f.item())).isTrue();
    }
    @Test void partialPaidWaivedOverdueFeesRemainUnchangedAndNeverDuplicate() {
        var f=fixture(true);
        var tx=new TransactionTemplate(transactions);
        var assessment=tx.execute(status->configuration.recordReturnFee(f.item(),f.reader(),configuration.previewReturnFee(OffsetDateTime.now().minusDays(2),OffsetDateTime.now())));
        jdbc.execute("ALTER TABLE reader_fees DISABLE TRIGGER trg_guard_reader_fee_waiver");
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd=3000,waived_amount_vnd=1000 WHERE id=?",assessment.feeId());
        jdbc.execute("ALTER TABLE reader_fees ENABLE TRIGGER trg_guard_reader_fee_waiver");
        var before=jdbc.queryForMap("SELECT amount_vnd,paid_amount_vnd,waived_amount_vnd FROM reader_fees WHERE id=?",assessment.feeId());
        var result=save(f,"DAMAGED");
        assertThat(result.overdueFee().feeId()).isEqualTo(assessment.feeId());
        assertThat(result.overdueFee().amountVnd()).isEqualTo(assessment.amountVnd());
        assertThat(jdbc.queryForMap("SELECT amount_vnd,paid_amount_vnd,waived_amount_vnd FROM reader_fees WHERE id=?",assessment.feeId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reader_fees WHERE return_item_id=?",Long.class,f.item())).isEqualTo(1);
    }
    @Test void overdueWithoutExistingFeeIsChargedOnce() {
        var f=fixture(true); var result=save(f,"LOST");
        assertThat(result.overdueFee().amountVnd()).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reader_fees WHERE return_item_id=?",Long.class,f.item())).isEqualTo(1);
    }
    @Test void multiCopyLoanStaysOpenThenClosesWithBothReasons() {
        var f=fixture(false);
        Long book=jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id=?",Long.class,f.copy());
        Long second=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT min(id) FROM shelves),'AVAILABLE',CURRENT_DATE,100000,'GOOD') RETURNING id",Long.class,book,"S4031-"+UUID.randomUUID());
        loans.insertItem(f.loan(),second,OffsetDateTime.now().minusDays(8),OffsetDateTime.now().plusDays(3));
        assertThat(save(f,"LOST").loanStatus()).isEqualTo("BORROWED");
        Long item=jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=? AND book_copy_id=?",Long.class,f.loan(),second);
        var p=service.preview(item,"DAMAGED",f.staff());
        var result=service.confirm(item,"DAMAGED","Rách toàn bộ sách",p.token(),List.of(),f.staff());
        assertThat(result.loanStatus()).isEqualTo("MIXED_INCIDENT");
    }
    @Test void validPhotoStoredFakePhotoRejectedAndHistoryImmutable() {
        var f=fixture(false); var p=service.preview(f.item(),"DAMAGED",f.staff());
        var fake=new MockMultipartFile("files","fake.png","image/png",new byte[]{1,2,3});
        code(()->service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(fake),f.staff()),"INVALID_INCIDENT_IMAGES");
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGP8//8/AwMDEwMDAwMDAwAkBgMB/DXemwAAAABJRU5ErkJggg==");
        var result=service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(new MockMultipartFile("files","a.png","image/png",png)),f.staff());
        assertThat(result.photos()).hasSize(1);
        assertThat(service.image(result.id(),result.photos().get(0).id(),f.staff()).data()).isEqualTo(png);
        assertThatThrownBy(()->jdbc.update("DELETE FROM book_incidents WHERE id=?",result.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(()->jdbc.update("UPDATE book_incidents SET description='Khác' WHERE id=?",result.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM book_incident_images WHERE incident_id=?",result.id())).isInstanceOf(Exception.class);
    }
    @Test void lateFailureRollsBackEveryFeeIncidentImageCopyAndClosure() {
        var f=fixture(true); String constraint="s4031_fail_"+f.copy();
        try {
            jdbc.execute("ALTER TABLE book_copy_status_history ADD CONSTRAINT "+constraint+" CHECK(book_copy_id<>"+f.copy()+")");
            assertThatThrownBy(()->save(f,"LOST")).isInstanceOf(Exception.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM book_incidents WHERE item_id=?",Long.class,f.item())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reader_fees WHERE reader_user_id=?",Long.class,f.reader())).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id=?",String.class,f.copy())).isEqualTo("BORROWED");
            assertThat(jdbc.queryForObject("SELECT returned_at IS NULL FROM loan_items WHERE id=?",Boolean.class,f.item())).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM return_fee_assessments WHERE item_id=?",Long.class,f.item())).isZero();
        } finally { jdbc.execute("ALTER TABLE book_copy_status_history DROP CONSTRAINT "+constraint); }
    }
    @Test void twoLibrariansCanRecordSameCopyOnlyOnce() throws Exception {
        var f=fixture(false); var p=service.preview(f.item(),"LOST",f.staff());
        var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->attempt(f,p,f.staff(),barrier)); var b=pool.submit(()->attempt(f,p,f.other(),barrier));
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","ITEM_ALREADY_CLOSED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM book_incidents WHERE copy_id=?",Long.class,f.copy())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reader_fees WHERE compensation_item_id=?",Long.class,f.item())).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    String attempt(Fixture f,Preview p,Long actor,CyclicBarrier barrier) throws Exception {
        barrier.await(5,TimeUnit.SECONDS);
        try { service.confirm(f.item(),"LOST","Mất",p.token(),List.of(),actor); return "OK"; }
        catch(ApiException e) { return e.getCode(); }
    }
    @Test void adjustmentPersistsActualDebtSnapshotsAndAuditAtomically() {
        for(String value:List.of("100000","250000","0","10000000")) {
            var f=fixture(true); var p=service.preview(f.item(),"DAMAGED",f.staff());
            var result=service.confirm(f.item(),"DAMAGED","Hỏng bìa",p.token(),List.of(),f.staff(),value,"Còn phục hồi được");
            assertThat(result.automaticCompensationVnd()).isEqualTo(150000);
            assertThat(result.compensationVnd()).isEqualTo(Long.parseLong(value));
            assertThat(result.adjustmentReason()).isEqualTo("Còn phục hồi được");
            assertThat(result.actorUserId()).isEqualTo(f.staff());
            assertThat(result.item().copyStatus()).isEqualTo("REMOVED");
            assertThat(result.loanStatus()).isEqualTo("DAMAGED");
            assertThat(result.overdueFee().amountVnd()).isEqualTo(p.overdueFee().amountVnd());
            assertThat(jdbc.queryForObject("SELECT amount_vnd FROM reader_fees WHERE id=?",Long.class,result.compensationFeeId())).isEqualTo(Long.parseLong(value));
            assertThat(jdbc.queryForObject("SELECT sum(amount_vnd-paid_amount_vnd-waived_amount_vnd) FROM reader_fees WHERE reader_user_id=?",Long.class,f.reader())).isEqualTo(Long.parseLong(value)+result.overdueFee().amountVnd());
            var audit=jdbc.queryForMap("SELECT after_data->>'beforeAmountVnd' AS before,after_data->>'afterAmountVnd' AS after,after_data->>'reason' AS reason FROM audit_logs WHERE action='COMPENSATION_ADJUSTED' AND entity_id=?",result.id().toString());
            assertThat(audit.get("before")).isEqualTo("150000");assertThat(audit.get("after")).isEqualTo(value);
            assertThat(audit.get("reason")).isEqualTo("Còn phục hồi được");
            assertThatThrownBy(()->jdbc.update("UPDATE reader_fees SET amount_vnd=1 WHERE id=?",result.compensationFeeId())).isInstanceOf(Exception.class);
            assertThatThrownBy(()->jdbc.update("DELETE FROM reader_fees WHERE id=?",result.compensationFeeId())).isInstanceOf(Exception.class);
            assertThatThrownBy(()->jdbc.update("UPDATE book_incidents SET adjustment_reason='Khác' WHERE id=?",result.id())).isInstanceOf(Exception.class);
        }
    }
    @Test void invalidOrUnauthorizedAdjustmentNeverRecordsAnything() {
        var f=fixture(false);var p=service.preview(f.item(),"DAMAGED",f.staff());
        code(()->service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(),f.staff(),"-1","Lý do"),"INVALID_COMPENSATION_AMOUNT");
        code(()->service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(),f.staff(),"10000001","Lý do"),"COMPENSATION_ADJUSTMENT_LIMIT");
        code(()->service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(),f.staff(),"0"," "),"INVALID_ADJUSTMENT_REASON");
        for(Long actor:List.of(f.manager(),f.reader())) code(()->service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(),actor,"0","Lý do"),"FORBIDDEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reader_fees WHERE reader_user_id=?",Long.class,f.reader())).isZero();
        assertThat(jdbc.queryForObject("SELECT returned_at IS NULL FROM loan_items WHERE id=?",Boolean.class,f.item())).isTrue();
    }
    @Test void lateFailureRollsBackAdjustmentAuditToo() {
        var f=fixture(true);var p=service.preview(f.item(),"DAMAGED",f.staff());String constraint="s4032_fail_"+f.copy();
        try {
            jdbc.execute("ALTER TABLE book_copy_status_history ADD CONSTRAINT "+constraint+" CHECK(book_copy_id<>"+f.copy()+")");
            assertThatThrownBy(()->service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(),f.staff(),"100000","Lý do")).isInstanceOf(Exception.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='COMPENSATION_ADJUSTED' AND actor_user_id=?",Long.class,f.staff())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reader_fees WHERE reader_user_id=?",Long.class,f.reader())).isZero();
        } finally {jdbc.execute("ALTER TABLE book_copy_status_history DROP CONSTRAINT "+constraint);}
    }

    @Test void adjustmentDoesNotRewritePaidOrWaivedOverdueFee() {
        var f=fixture(true);var tx=new TransactionTemplate(transactions);
        var fee=tx.execute(status->configuration.recordReturnFee(f.item(),f.reader(),configuration.previewReturnFee(OffsetDateTime.now().minusDays(2),OffsetDateTime.now())));
        jdbc.execute("ALTER TABLE reader_fees DISABLE TRIGGER trg_guard_reader_fee_waiver");
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd=3000,waived_amount_vnd=1000 WHERE id=?",fee.feeId());
        jdbc.execute("ALTER TABLE reader_fees ENABLE TRIGGER trg_guard_reader_fee_waiver");
        var before=jdbc.queryForMap("SELECT amount_vnd,paid_amount_vnd,waived_amount_vnd FROM reader_fees WHERE id=?",fee.feeId());
        var p=service.preview(f.item(),"DAMAGED",f.staff());
        var result=service.confirm(f.item(),"DAMAGED","Hỏng",p.token(),List.of(),f.staff(),"100000","Còn phục hồi được");
        assertThat(result.overdueFee().feeId()).isEqualTo(fee.feeId());
        assertThat(result.overdueFee().amountVnd()).isEqualTo(fee.amountVnd());
        assertThat(jdbc.queryForMap("SELECT amount_vnd,paid_amount_vnd,waived_amount_vnd FROM reader_fees WHERE id=?",fee.feeId())).isEqualTo(before);
    }

    @Test void adminCanAdjustCompensationAndAuditRecordedForEveryIncident() {
        var f=fixture(true);
        Long admin=user("ADMIN");
        var p=service.preview(f.item(),"LOST",admin);
        var result=service.confirm(f.item(),"LOST","Mất bởi bạn đọc",p.token(),List.of(),admin,"120000","Admin giảm giá hỗ trợ");
        assertThat(result.compensationVnd()).isEqualTo(120000);
        assertThat(result.automaticCompensationVnd()).isEqualTo(200000);
        assertThat(result.adjustmentReason()).isEqualTo("Admin giảm giá hỗ trợ");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='INCIDENT_RECORDED' AND entity_id=?",Long.class,result.id().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='COMPENSATION_ADJUSTED' AND entity_id=?",Long.class,result.id().toString())).isEqualTo(1);
    }
}
