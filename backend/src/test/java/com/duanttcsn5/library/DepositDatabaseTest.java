package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.DepositDtos.*;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.bootstrap-admin.password=","app.scheduling.enabled=false",
        "app.permission-review.launch-enabled=false","app.pickup-email.automatic-enabled=false",
        "app.deposits.activation-enabled=true","spring.main.allow-bean-definition-overriding=true"})
@EnabledIfEnvironmentVariable(named="S4_09_2_DB_TEST",matches="true")
@ContextConfiguration(initializers=FullPermissionDatabaseTest.DatabaseGuard.class)
@Import(FullPermissionDatabaseTest.SafeTransport.class)
@Transactional
class DepositDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DepositRepository deposits;
    @Autowired DepositService service;
    @Autowired LoanRepository loans;
    @Autowired PlatformTransactionManager transactions;
    Long user(String role) {return jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,status) SELECT id,'Deposit test',?,'ACTIVE' FROM roles WHERE code=? RETURNING id",Long.class,UUID.randomUUID()+"@example.invalid",role);}
    Long copy(long price) {
        Long book=jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES('Deposit test',(SELECT min(id) FROM authors),(SELECT min(id) FROM categories)) RETURNING id",Long.class);
        return jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES(?,?,(SELECT min(id) FROM shelves),'AVAILABLE',CURRENT_DATE,?,'GOOD') RETURNING id",Long.class,book,"DEP-"+UUID.randomUUID(),price);
    }
    record Fixture(Long admin,Long reader,Long copy,Long account) {}
    Fixture fixture() {
        Long admin=user("ADMIN"),reader=user("READER"),copy=copy(100000);
        deposits.policy(new PolicyRequest(new BigDecimal("50"),null),admin);
        Long account=deposits.account(reader,copy,deposits.current(),new BigDecimal("100000"),50000,admin);
        return new Fixture(admin,reader,copy,account);
    }
    TransactionRequest request(String kind,long amount,Long... accounts) {return new TransactionRequest(UUID.randomUUID(),List.of(accounts),amount,kind);}
    Long handover(Fixture f) {
        var now=OffsetDateTime.now();Long loan=loans.insert(null,f.reader(),f.admin(),"DEP-"+UUID.randomUUID(),now);
        loans.insertItem(loan,f.copy(),now,now.plusDays(7));
        return jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?",Long.class,loan);
    }
    void returned(Long item) {jdbc.update("UPDATE loan_items SET returned_at=clock_timestamp(),resolution_reason='RETURNED' WHERE id=?",item);}
    @Test void collectionIsIdempotentAndHandoverUsesExactlyOneFundedAllocation() {
        var f=fixture();var request=request("COLLECT",50000,f.account());
        var first=service.transact(f.reader(),"COLLECT",request,f.admin());
        assertEquals(first.id(),service.transact(f.reader(),"COLLECT",request,f.admin()).id());
        assertThrows(Exception.class,()->service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),f.admin()));
        Long item=handover(f);
        assertEquals(f.account(),jdbc.queryForObject("SELECT account_id FROM deposit_handovers WHERE item_id=?",Long.class,item));
        assertEquals(50000,deposits.find(f.account()).orElseThrow().heldVnd());
        assertThrows(Exception.class,()->service.transact(f.reader(),"REFUND",request("REFUND",50000,f.account()),f.admin()));
    }
    @Test void noDepositBlocksHandoverAndStatementRollbackPreservesCopy() {
        var f=fixture();var savepoint=jdbc.execute((java.sql.Connection c)->c.setSavepoint());
        assertThrows(Exception.class,()->handover(f));
        jdbc.execute((java.sql.Connection c)->{c.rollback(savepoint);return null;});
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM book_copies WHERE id=?",String.class,f.copy()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM loan_items WHERE book_copy_id=?",Integer.class,f.copy()));
    }
    @Test void partialReturnOnlyReleasesReturnedCopyAndRefundIsIdempotent() {
        var f=fixture();Long copy2=copy(200000);
        Long account2=deposits.account(f.reader(),copy2,deposits.current(),new BigDecimal("200000"),100000,f.admin());
        service.transact(f.reader(),"COLLECT",request("COLLECT",150000,f.account(),account2),f.admin());
        Long item1=handover(f);handover(new Fixture(f.admin(),f.reader(),copy2,account2));returned(item1);
        var request=request("REFUND",50000,f.account());var refund=service.transact(f.reader(),"REFUND",request,f.admin());
        assertEquals(refund.id(),service.transact(f.reader(),"REFUND",request,f.admin()).id());
        assertEquals(0,deposits.find(f.account()).orElseThrow().heldVnd());
        assertEquals(100000,deposits.find(account2).orElseThrow().heldVnd());
        assertThrows(Exception.class,()->service.transact(f.reader(),"REFUND",request("REFUND",100000,account2),f.admin()));
    }
    @Test void confirmedOverdueDebtIsOffsetThroughExistingReceiptThenActualRefund() {
        var f=fixture();service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),f.admin());
        Long item=handover(f);returned(item);
        Long fee=jdbc.queryForObject("INSERT INTO reader_fees(reader_user_id,return_item_id,amount_vnd,description) VALUES(?,?,15000,'Deposit test') RETURNING id",Long.class,f.reader(),item);
        assertThrows(Exception.class,()->service.transact(f.reader(),"REFUND",request("REFUND",50000,f.account()),f.admin()));
        var offset=service.transact(f.reader(),"DEDUCT",request("DEDUCT",15000,f.account()),f.admin());
        assertNotNull(offset.feePaymentId());
        assertEquals("DEPOSIT_OFFSET",jdbc.queryForObject("SELECT funding_source FROM fee_payments WHERE id=?",String.class,offset.feePaymentId()));
        assertEquals(15000,jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?",Long.class,fee));
        assertEquals(35000,deposits.find(f.account()).orElseThrow().heldVnd());
        service.transact(f.reader(),"REFUND",request("REFUND",35000,f.account()),f.admin());
        assertEquals(0,deposits.find(f.account()).orElseThrow().heldVnd());
    }
    @Test void cancellationBeforeHandoverRefundsCashAndCannotReuseRefundedDeposit() {
        var f=fixture();service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),f.admin());
        service.transact(f.reader(),"REFUND",request("REFUND",50000,f.account()),f.admin());
        assertTrue(deposits.reusable(f.reader(),f.copy()).isEmpty());
        assertThrows(Exception.class,()->service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),f.admin()));
    }
    @Test void compensationCanExceedDepositAndLeavesExistingFeeDebtForCollection() {
        var f=fixture();service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),f.admin());
        Long item=handover(f);returned(item);
        Long fee=jdbc.queryForObject("INSERT INTO reader_fees(reader_user_id,compensation_item_id,amount_vnd,description) VALUES(?,?,120000,'Confirmed compensation') RETURNING id",Long.class,f.reader(),item);
        var request=request("DEDUCT",50000,f.account());var first=service.transact(f.reader(),"DEDUCT",request,f.admin());
        assertEquals(first.id(),service.transact(f.reader(),"DEDUCT",request,f.admin()).id());
        assertEquals(70000,jdbc.queryForObject("SELECT amount_vnd-paid_amount_vnd-waived_amount_vnd FROM reader_fees WHERE id=?",Long.class,fee));
        assertEquals(0,deposits.find(f.account()).orElseThrow().heldVnd());
        assertThrows(Exception.class,()->service.transact(f.reader(),"REFUND",request("REFUND",50000,f.account()),f.admin()));
    }
    @Test void legacyHandoverIsNotBackfilledAndPricePolicyChangesDoNotRewriteAccount() {
        Long admin=user("ADMIN"),reader=user("READER"),oldCopy=copy(100000);
        var old=new Fixture(admin,reader,oldCopy,null);Long item=handover(old);
        var f=fixture();
        jdbc.update("UPDATE book_copies SET cover_price=200000 WHERE id=?",f.copy());
        deposits.policy(new PolicyRequest(new BigDecimal("75"),null),f.admin());
        var saved=deposits.find(f.account()).orElseThrow();
        assertEquals(50000,saved.requiredVnd());assertEquals(new BigDecimal("100000.00"),saved.coverPrice());
        assertEquals(new BigDecimal("50.0000"),saved.percentage());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM deposit_handovers WHERE item_id=?",Integer.class,item));
    }
    @Test void committedEventSnapshotsKeepLoanReturnAndRefundStatesSeparate() {
        var f=fixture();service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),f.admin());
        Long item=handover(f);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        returned(item);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        service.transact(f.reader(),"REFUND",request("REFUND",50000,f.account()),f.admin());
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        var mapper=new tools.jackson.databind.ObjectMapper();
        var returned=mapper.readTree(jdbc.queryForObject("SELECT financial_snapshot::text FROM ready_pickup_notifications WHERE reader_id=? AND notification_type='DEPOSIT_RETURN'",String.class,f.reader()));
        assertEquals(0,returned.path("items").get(0).path("refundedVnd").asLong());
        assertEquals(50000,returned.path("items").get(0).path("heldVnd").asLong());
        var loan=mapper.readTree(jdbc.queryForObject("SELECT financial_snapshot::text FROM ready_pickup_notifications WHERE reader_id=? AND notification_type='DEPOSIT_LOAN'",String.class,f.reader()));
        assertEquals(50000,loan.path("items").get(0).path("collectedVnd").asLong());
        assertTrue(loan.path("items").get(0).path("returnedAt").isNull());
        var history=service.history(f.reader(),0,f.reader());
        assertEquals(2,history.totalItems());assertEquals(2,history.items().size());
        assertEquals(f.account(),history.items().get(0).entries().get(0).accountId());
        var savepoint=jdbc.execute((java.sql.Connection c)->c.setSavepoint());
        assertThrows(Exception.class,()->jdbc.update("UPDATE ready_pickup_notifications SET financial_snapshot='{}'::jsonb WHERE reader_id=?",f.reader()));
        jdbc.execute((java.sql.Connection c)->{c.rollback(savepoint);return null;});
    }
    @Test void readersCannotReadOthersOrCollectAndManagerCannotCollect() {
        var f=fixture();Long other=user("READER"),manager=user("LIBRARY_MANAGER");
        assertEquals(1,service.accounts(f.reader(),"",0,f.reader()).totalItems());
        assertThrows(com.duanttcsn5.library.exception.ApiException.class,()->service.accounts(f.reader(),"",0,other));
        assertThrows(com.duanttcsn5.library.exception.ApiException.class,()->service.history(f.reader(),0,other));
        for(Long actor:List.of(f.reader(),other,manager))assertThrows(com.duanttcsn5.library.exception.ApiException.class,()->service.transact(f.reader(),"COLLECT",request("COLLECT",50000,f.account()),actor));
        assertEquals(0,deposits.find(f.account()).orElseThrow().collectedVnd());
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentCashConfirmationsCommitOneLedgerTransactionAndOneRefund() throws Exception {
        // Only a future policy is committed: no mandatory deposit leaks into other API fixtures.
        var tx=new TransactionTemplate(transactions);
        var f=tx.execute(s->{
            Long admin=user("ADMIN"),reader=user("READER"),copy=copy(100000);
            deposits.policy(new PolicyRequest(new BigDecimal("50"),OffsetDateTime.now().plusYears(5)),admin);
            var policy=deposits.policies(0).history().get(0);
            Long account=deposits.account(reader,copy,policy,new BigDecimal("100000"),50000,admin);
            return new Fixture(admin,reader,copy,account);
        });
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var request=request("COLLECT",50000,f.account());
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> collect=()->{start.await();return service.transact(f.reader(),"COLLECT",request,f.admin()).id();};
            var first=executor.submit(collect);var second=executor.submit(collect);start.countDown();
            assertEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM deposit_transactions WHERE reader_user_id=?",Integer.class,f.reader()));
            var refund=request("REFUND",50000,f.account());
            var r1=executor.submit(()->service.transact(f.reader(),"REFUND",refund,f.admin()).id());
            var r2=executor.submit(()->service.transact(f.reader(),"REFUND",refund,f.admin()).id());
            assertEquals(r1.get(20,java.util.concurrent.TimeUnit.SECONDS),r2.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,deposits.find(f.account()).orElseThrow().heldVnd());
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM deposit_transactions WHERE reader_user_id=?",Integer.class,f.reader()));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ready_pickup_notifications WHERE reader_id=? AND notification_type='DEPOSIT_TRANSACTION'",Integer.class,f.reader()));
            String collection=jdbc.queryForObject("SELECT financial_snapshot::text FROM ready_pickup_notifications WHERE reader_id=? AND financial_snapshot->>'kind'='COLLECT'",String.class,f.reader());
            var data=new tools.jackson.databind.ObjectMapper().readTree(collection);
            assertEquals(0,data.path("items").get(0).path("refundedVnd").asLong());
            assertEquals(50000,data.path("items").get(0).path("heldVnd").asLong());
        } finally {executor.shutdownNow();}
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void incompleteTransactionRollsBackAtCommit() {
        var tx=new TransactionTemplate(transactions);var token=UUID.randomUUID();
        Long admin=tx.execute(s->user("ADMIN")),reader=tx.execute(s->user("READER"));
        assertThrows(Exception.class,()->tx.execute(s->deposits.transaction(reader,token,"0".repeat(64),"COLLECT",50000,admin,"Test",null)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM deposit_transactions WHERE reader_user_id=?",Integer.class,reader));
    }
}
