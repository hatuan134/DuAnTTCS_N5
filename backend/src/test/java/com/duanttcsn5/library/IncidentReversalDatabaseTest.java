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
@EnabledIfEnvironmentVariable(named="S4_03_3_DB_TEST",matches="true")
class IncidentReversalDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookIncidentService service;
    @Autowired LoanRepository loans;
    @Autowired com.duanttcsn5.library.repository.BookCopyRepository copies;
    @Autowired LibraryConfigurationService configuration;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.duanttcsn5.library.service.FeePaymentService payments;
    @Autowired com.duanttcsn5.library.repository.FeePaymentRepository paymentRows;
    @Autowired com.duanttcsn5.library.service.LoanService loanService;
    record Fixture(Long staff,Long other,Long manager,Long reader,Long loan,Long item,Long copy) {}
    @BeforeEach void configure() {
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=2,damaged_factor=1.5 WHERE id=1");
        jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd=5000 WHERE id=1");
        jdbc.update("UPDATE library_weekly_schedule SET is_open=true,open_time='08:00',close_time='17:00'");
        jdbc.update("DELETE FROM library_closed_dates WHERE closed_date BETWEEN CURRENT_DATE-20 AND CURRENT_DATE+1");
    }
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,?,?, 'ACTIVE' FROM roles WHERE code=? RETURNING id",
                Long.class,"S4033 "+role,UUID.randomUUID()+"@example.invalid",role);
    }
    Fixture fixture(boolean overdue) {
        Long staff=user("LIBRARIAN"),other=user("LIBRARIAN"),manager=user("LIBRARY_MANAGER"),reader=user("READER");
        Long book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT min(id) FROM authors),(SELECT min(id) FROM categories)) RETURNING id",
                Long.class,"S4033 "+UUID.randomUUID());
        Long copy=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT min(id) FROM shelves),'AVAILABLE',CURRENT_DATE-10,100000,'GOOD') RETURNING id",
                Long.class,book,"S4033-"+UUID.randomUUID());
        OffsetDateTime borrowed=OffsetDateTime.now().minusDays(8);
        Long loan=loans.insert(null,reader,staff,"S4033-"+UUID.randomUUID(),borrowed);
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
    Adjustment request(Fixture f,Incident i,String target) {
        return service.requestAdjustment(i.id(),new AdjustmentRequest("Tìm lại hoặc xác định ghi nhận nhầm","MISTAKEN",target),f.staff());
    }
    @Test void unpaidPartialAndFullCollectionsRemainImmutableAndRefundsAreSeparate() {
        for(long paid:List.of(0L,50000L,200000L)) {
            Fixture f=fixture(false); Incident i=save(f,"LOST");
            com.duanttcsn5.library.dto.loan.FeeReceiptResponse receipt=null;
            if(paid>0) {
                var debt=payments.detail(f.reader(),f.staff());
                receipt=payments.collectPartial(f.reader(),new com.duanttcsn5.library.dto.loan.CollectPartialFeesRequest(
                    UUID.randomUUID(),debt.totalOutstandingVnd(),debt.debtToken(),paid),f.staff());
            }
            Adjustment a=request(f,i,"AVAILABLE");
            assertThat(paymentRows.outstanding(f.reader(),false)).hasSize(paid==200000?0:1);
            Adjustment result=service.confirmAdjustment(i.id(),a.id(),f.manager());
            assertThat(result.reversedVnd()).isEqualTo(200000L);
            assertThat(result.cancelledDebtVnd()).isEqualTo(200000-paid);
            assertThat(result.refundDueVnd()).isEqualTo(paid);
            assertThat(paymentRows.outstanding(f.reader(),false)).isEmpty();
            assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(jdbc.queryForObject("SELECT amount_vnd FROM reader_fees WHERE id=?",Long.class,i.compensationFeeId())).isEqualTo(200000);
            assertThat(jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?",Long.class,i.compensationFeeId())).isEqualTo(paid);
            if(receipt!=null) assertThat(payments.receipt(receipt.id(),f.staff())).isEqualTo(receipt);
            assertThatThrownBy(()->jdbc.update("UPDATE reader_fees SET paid_amount_vnd=1 WHERE id=?",i.compensationFeeId())).isInstanceOf(Exception.class);
        }
    }
    @Test void lostAndDamagedRestoreAvailabilityOrRemainInRepair() {
        for(String type:List.of("LOST","DAMAGED")) for(String target:List.of("AVAILABLE","REPAIR","BORROWED")) {
            Fixture f=fixture(false); Incident i=save(f,type); Adjustment a=request(f,i,target);
            service.confirmAdjustment(i.id(),a.id(),f.manager());
            var detail=service.detail(i.id(),f.manager());
            assertThat(detail.item().copyStatus()).isEqualTo(target);
            assertThat(detail.loanStatus()).isEqualTo(target.equals("BORROWED")?"BORROWED":"RETURNED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE id=? AND status='AVAILABLE'",Long.class,f.copy())).isEqualTo(target.equals("AVAILABLE")?1:0);
            assertThat(detail.description()).isEqualTo(i.description());
            assertThat(detail.compensationVnd()).isEqualTo(i.compensationVnd());
        }
    }
    @Test void foundBookAndMultipleItemsKeepLoanOpenUntilAllCopiesResolved() {
        Fixture f=fixture(false);
        Long bookId=jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id=?",Long.class,f.copy());
        Long copy2=jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,(SELECT min(id) FROM shelves),'AVAILABLE',CURRENT_DATE-10,100000,'GOOD') RETURNING id",Long.class,bookId,"S4033-"+UUID.randomUUID());
        loans.insertItem(f.loan(),copy2,OffsetDateTime.now().minusDays(8),OffsetDateTime.now().plusDays(3));
        Incident i=save(f,"LOST");
        Adjustment a=service.requestAdjustment(i.id(),new AdjustmentRequest("Tìm lại sách","FOUND","AVAILABLE"),f.staff());
        service.confirmAdjustment(i.id(),a.id(),f.manager());
        assertThat(service.detail(i.id(),f.manager()).loanStatus()).isEqualTo("BORROWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loan_items WHERE loan_id=? AND returned_at IS NULL",Long.class,f.loan())).isEqualTo(1);
    }
    @Test void reopeningThenReturningDoesNotRecalculateOrDuplicateOriginalOverdueFee() {
        Fixture f=fixture(true); Incident i=save(f,"LOST");
        String original=jdbc.queryForObject("SELECT snapshot::text FROM return_fee_assessments WHERE item_id=?",String.class,f.item());
        Adjustment a=request(f,i,"BORROWED"); service.confirmAdjustment(i.id(),a.id(),f.manager());
        var lookup=loanService.lookupReturn(i.item().barcode(),f.staff());
        assertThat(lookup.fee()).isEqualTo(i.overdueFee());
        loanService.confirmReturn(new com.duanttcsn5.library.dto.loan.ConfirmReturnRequest(i.item().barcode(),f.item()),f.staff());
        assertThat(jdbc.queryForObject("SELECT snapshot::text FROM return_fee_assessments WHERE item_id=?",String.class,f.item())).isEqualTo(original);
        assertThat(paymentRows.outstanding(f.reader(),false)).extracting(x->x.feeType()).containsExactly("OVERDUE");
    }
    @Test void permissionsReasonAndRepeatedReversalAreEnforced() {
        Fixture f=fixture(false); Incident i=save(f,"LOST");
        for(String reason:List.of(""," ","x".repeat(1901)))
            code(()->service.requestAdjustment(i.id(),new AdjustmentRequest(reason,"MISTAKEN","AVAILABLE"),f.staff()),"INVALID_ADJUSTMENT_REASON");
        code(()->service.requestAdjustment(i.id(),new AdjustmentRequest("Lý do","MISTAKEN","AVAILABLE"),f.reader()),"FORBIDDEN");
        code(()->service.requestAdjustment(i.id(),new AdjustmentRequest("Lý do","FOUND","UNKNOWN"),f.staff()),"INVALID_ADJUSTMENT");
        Adjustment a=request(f,i,"AVAILABLE");
        code(()->service.confirmAdjustment(i.id(),a.id(),f.staff()),"FORBIDDEN");
        code(()->service.confirmAdjustment(i.id(),a.id(),f.reader()),"FORBIDDEN");
        code(()->request(f,i,"REPAIR"),"ADJUSTMENT_EXISTS");
        service.confirmAdjustment(i.id(),a.id(),f.manager());
        code(()->service.confirmAdjustment(i.id(),a.id(),f.manager()),"ADJUSTMENT_CONFIRMED");
        assertThat(service.adjustments(i.id(),f.staff())).hasSize(1);
    }
    @Test void concurrentConfirmationsProduceExactlyOneLedgerEffect() throws Exception {
        Fixture f=fixture(false); Incident i=save(f,"LOST"); Adjustment a=request(f,i,"AVAILABLE");
        CyclicBarrier barrier=new CyclicBarrier(2); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> run=()-> {barrier.await(5,TimeUnit.SECONDS);try {service.confirmAdjustment(i.id(),a.id(),f.manager());return "OK";}
                catch(ApiException e){return e.getCode();}};
            var one=pool.submit(run); var two=pool.submit(run);
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","ADJUSTMENT_CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='INCIDENT_REVERSED' AND entity_id=?",Long.class,a.id().toString())).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void failureMidTransactionRollsBackThenRetryWorksAndHistoryCannotBeDeleted() {
        Fixture f=fixture(false); Incident i=save(f,"LOST"); Adjustment a=request(f,i,"AVAILABLE");
        String constraint="s4033_failure_"+f.copy();
        try {
            jdbc.execute("ALTER TABLE book_copy_status_history ADD CONSTRAINT "+constraint+" CHECK(book_copy_id<>"+f.copy()+" OR new_status='REMOVED')");
            assertThatThrownBy(()->service.confirmAdjustment(i.id(),a.id(),f.manager())).isInstanceOf(Exception.class);
            assertThat(service.adjustments(i.id(),f.staff()).get(0).status()).isEqualTo("PENDING");
            assertThat(service.detail(i.id(),f.staff()).item().copyStatus()).isEqualTo("REMOVED");
            assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo(BigDecimal.valueOf(200000));
        } finally {jdbc.execute("ALTER TABLE book_copy_status_history DROP CONSTRAINT "+constraint);}
        Adjustment result=service.confirmAdjustment(i.id(),a.id(),f.manager());
        assertThat(result.beforeData()).contains("REMOVED"); assertThat(result.afterData()).contains("AVAILABLE");
        assertThatThrownBy(()->jdbc.update("DELETE FROM book_incidents WHERE id=?",i.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM book_incident_adjustments WHERE id=?",a.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(()->jdbc.update("UPDATE book_incident_adjustments SET reason='X' WHERE id=?",a.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM audit_logs WHERE action='INCIDENT_REVERSED' AND entity_id=?",a.id().toString())).isInstanceOf(Exception.class);
    }
    @Test void recoveredAvailableCopyGoesToEligibleFifoWaiterAndDoesNotInflateFreeCount() {
        Fixture f=fixture(false); Incident i=save(f,"LOST"); Long reader=user("READER");
        Long type=jdbc.queryForObject("INSERT INTO card_types(name,max_books,loan_days,max_renewals,renewal_days) VALUES (?,5,14,1,7) RETURNING id",Long.class,"S4033-"+UUID.randomUUID());
        jdbc.update("INSERT INTO library_cards(card_number,user_id,card_type_id,issued_at,expires_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
            "S4033-"+UUID.randomUUID(),reader,type,LocalDate.now().minusDays(1),LocalDate.now().plusYears(1));
        Long bookId=jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id=?",Long.class,f.copy());
        Long reservation=jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id,status,reserved_at) VALUES (?,?,'PENDING',clock_timestamp()-interval '1 minute') RETURNING id",Long.class,bookId,reader);
        Adjustment a=request(f,i,"AVAILABLE");
        assertThat(service.adjustmentPreview(i.id(),"AVAILABLE",f.manager()).copyStatus()).isEqualTo("HELD");
        service.confirmAdjustment(i.id(),a.id(),f.manager());
        assertThat(service.detail(i.id(),f.staff()).item().copyStatus()).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT book_copy_id FROM book_reservations WHERE id=? AND status='READY_FOR_PICKUP'",Long.class,reservation)).isEqualTo(f.copy());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE id=? AND status='AVAILABLE'",Long.class,f.copy())).isZero();
    }

    @Test void adminMayRequestAndConfirmButInactiveAccountsAndReadersCannot() {
        Fixture f=fixture(false); Incident incident=save(f,"LOST"); Long admin=user("ADMIN");
        code(()->service.adjustments(incident.id(),f.reader()),"FORBIDDEN");
        Adjustment a=service.requestAdjustment(incident.id(),new AdjustmentRequest("Xác nhận lại tình trạng","REASSESSED","REPAIR"),admin);
        jdbc.update("UPDATE users SET status='LOCKED' WHERE id=?",admin);
        code(()->service.confirmAdjustment(incident.id(),a.id(),admin),"FORBIDDEN");
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=?",admin);
        assertThat(service.confirmAdjustment(incident.id(),a.id(),admin).status()).isEqualTo("CONFIRMED");
    }
    @Test void paymentRacingConfirmationCannotLeaveDebtOrLoseRefundObligation() throws Exception {
        Fixture f=fixture(false); Incident incident=save(f,"LOST"); Adjustment a=request(f,incident,"AVAILABLE");
        var debt=payments.detail(f.reader(),f.staff()); CyclicBarrier start=new CyclicBarrier(2);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var collect=pool.submit(()-> {start.await(5,TimeUnit.SECONDS);try {
                payments.collectPartial(f.reader(),new com.duanttcsn5.library.dto.loan.CollectPartialFeesRequest(UUID.randomUUID(),debt.totalOutstandingVnd(),debt.debtToken(),50000),f.staff());return "PAID";
            } catch(ApiException e){return e.getCode();}});
            var reverse=pool.submit(()-> {start.await(5,TimeUnit.SECONDS);return service.confirmAdjustment(incident.id(),a.id(),f.manager());});
            String payment=collect.get(15,TimeUnit.SECONDS); Adjustment result=reverse.get(15,TimeUnit.SECONDS);
            assertThat(payment).isIn("PAID","NO_OUTSTANDING_FEES","FEE_DEBT_CHANGED");
            assertThat(result.refundDueVnd()).isEqualTo("PAID".equals(payment)?50000L:0L);
            assertThat(result.cancelledDebtVnd()+result.refundDueVnd()).isEqualTo(200000);
            assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo(BigDecimal.ZERO);
        } finally {pool.shutdownNow();}
    }

}
