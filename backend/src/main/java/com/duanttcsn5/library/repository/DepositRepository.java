package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.DepositDtos.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DepositRepository {
    private final JdbcTemplate jdbc;
    public DepositRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final RowMapper<Policy> POLICY=(rs,n)->new Policy(rs.getLong("id"),rs.getBigDecimal("percentage"),
        rs.getObject("effective_at",OffsetDateTime.class),rs.getObject("created_at",OffsetDateTime.class),
        rs.getLong("created_by"),rs.getString("full_name"),rs.getObject("previous_policy_id",Long.class));
    private static final String POLICY_SELECT="SELECT p.*,u.full_name FROM deposit_policies p JOIN users u ON u.id=p.created_by ";
    public Policy current() { return jdbc.query(POLICY_SELECT+" WHERE effective_at<=statement_timestamp() ORDER BY effective_at DESC,p.id DESC LIMIT 1",POLICY).stream().findFirst().orElse(null); }
    public Policies policies(int page) { return new Policies(current(),jdbc.query(POLICY_SELECT+" ORDER BY p.id DESC LIMIT 20 OFFSET ?",POLICY,(long)page*20)); }
    public void policy(PolicyRequest r,Long actor) {
        jdbc.queryForList("SELECT id FROM library_overdue_fee_settings WHERE id=1 FOR UPDATE");
        jdbc.update("INSERT INTO deposit_policies(percentage,effective_at,created_by,previous_policy_id) VALUES (?,COALESCE(?,statement_timestamp()),?,(SELECT max(id) FROM deposit_policies))",r.percentage(),r.effectiveAt(),actor);
    }
    public void lockPolicy() { jdbc.queryForList("SELECT id FROM library_overdue_fee_settings WHERE id=1 FOR SHARE"); }
    public record Copy(Long id,String barcode,BigDecimal price,String status) {}
    public Optional<Copy> copy(String barcode) {
        return jdbc.query("SELECT id,barcode,cover_price,status FROM book_copies WHERE barcode=?",(rs,n)->new Copy(rs.getLong(1),rs.getString(2),rs.getBigDecimal(3),rs.getString(4)),barcode).stream().findFirst();
    }
    public boolean holdAllows(Long reader,Long copy) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT NOT EXISTS(SELECT 1 FROM book_reservations WHERE book_copy_id=? AND status='READY_FOR_PICKUP'
                AND (reader_id<>? OR pickup_deadline IS NULL OR pickup_deadline<=clock_timestamp()))
            """,Boolean.class,copy,reader));
    }
    public Optional<Long> reusable(Long reader,Long copy) {
        return jdbc.query("""
            SELECT a.id FROM deposit_accounts a WHERE reader_user_id=? AND copy_id=?
            AND NOT EXISTS(SELECT 1 FROM deposit_handovers h WHERE h.account_id=a.id)
            AND NOT EXISTS(SELECT 1 FROM deposit_entries e JOIN deposit_transactions t ON t.id=e.transaction_id
                WHERE e.account_id=a.id AND t.kind<>'COLLECT') ORDER BY a.id DESC LIMIT 1
            """,(rs,n)->rs.getLong(1),reader,copy).stream().findFirst();
    }
    public Long account(Long reader,Long copy,Policy policy,BigDecimal price,long amount,Long actor) {
        return jdbc.queryForObject("INSERT INTO deposit_accounts(reader_user_id,copy_id,policy_id,cover_price,percentage,required_vnd,created_by) VALUES(?,?,?,?,?,?,?) RETURNING id",Long.class,reader,copy,policy.id(),price,policy.percentage(),amount,actor);
    }
    public void lockAccount(Long id) { jdbc.queryForList("SELECT id FROM deposit_accounts WHERE id=? FOR UPDATE",id); }
    private static final String ACCOUNT_SELECT="""
        SELECT a.*,u.full_name,c.barcode,b.title,card.card_number,h.item_id,li.loan_id,l.loan_number,li.returned_at,
            COALESCE(e.collected,0) collected,COALESCE(e.refunded,0) refunded,COALESCE(e.deducted,0) deducted,
            COALESCE(f.outstanding,0) outstanding
        FROM deposit_accounts a JOIN users u ON u.id=a.reader_user_id
        JOIN book_copies c ON c.id=a.copy_id JOIN books b ON b.id=c.book_id
        LEFT JOIN library_cards card ON card.user_id=a.reader_user_id
        LEFT JOIN deposit_handovers h ON h.account_id=a.id LEFT JOIN loan_items li ON li.id=h.item_id
        LEFT JOIN loans l ON l.id=li.loan_id
        LEFT JOIN LATERAL(SELECT sum(x.amount_vnd) FILTER(WHERE t.kind='COLLECT') collected,
            sum(x.amount_vnd) FILTER(WHERE t.kind='REFUND') refunded,sum(x.amount_vnd) FILTER(WHERE t.kind='DEDUCT') deducted
            FROM deposit_entries x JOIN deposit_transactions t ON t.id=x.transaction_id WHERE x.account_id=a.id) e ON true
        LEFT JOIN LATERAL(SELECT sum(f.amount_vnd-f.paid_amount_vnd-f.waived_amount_vnd) outstanding FROM reader_fees f
            WHERE (f.return_item_id=h.item_id OR f.compensation_item_id=h.item_id)
            AND NOT EXISTS(SELECT 1 FROM book_incidents i JOIN book_incident_adjustments x ON x.incident_id=i.id
                WHERE i.compensation_fee_id=f.id AND x.status='CONFIRMED')) f ON true
        """;
    private static final RowMapper<Account> ACCOUNT=(rs,n)->new Account(rs.getLong("id"),rs.getLong("reader_user_id"),rs.getString("full_name"),rs.getString("card_number"),rs.getLong("copy_id"),rs.getString("barcode"),rs.getString("title"),rs.getLong("policy_id"),rs.getBigDecimal("cover_price"),rs.getBigDecimal("percentage"),rs.getLong("required_vnd"),rs.getLong("collected"),rs.getLong("refunded"),rs.getLong("deducted"),rs.getLong("collected")-rs.getLong("refunded")-rs.getLong("deducted"),rs.getObject("item_id",Long.class),rs.getObject("loan_id",Long.class),rs.getString("loan_number"),rs.getObject("returned_at",OffsetDateTime.class),rs.getLong("outstanding"),rs.getObject("created_at",OffsetDateTime.class));
    public Optional<Account> find(Long id) { return jdbc.query(ACCOUNT_SELECT+" WHERE a.id=?",ACCOUNT,id).stream().findFirst(); }
    public Accounts accounts(Long reader,String query,int page) {
        String where=" WHERE (?::bigint IS NULL OR a.reader_user_id=?) AND strpos(lower(concat_ws(' ',u.full_name,card.card_number,c.barcode,b.title,l.loan_number)),lower(?))>0 ";
        var count=jdbc.queryForObject("SELECT count(*) FROM ("+ACCOUNT_SELECT+where+") data",Long.class,reader,reader,query);
        return new Accounts(jdbc.query(ACCOUNT_SELECT+where+" ORDER BY a.id DESC LIMIT 10 OFFSET ?",ACCOUNT,reader,reader,query,(long)page*10),page,10,count==null?0:count);
    }
    public record Replay(Long id,Long actor,String fingerprint) {}
    public Optional<Replay> replay(Long reader,UUID key) { return jdbc.query("SELECT id,actor_id,fingerprint FROM deposit_transactions WHERE reader_user_id=? AND request_key=?",(rs,n)->new Replay(rs.getLong(1),rs.getLong(2),rs.getString(3)),reader,key).stream().findFirst(); }
    public Long transaction(Long reader,UUID key,String hash,String kind,long total,Long actor,String name,Long payment) {
        return jdbc.queryForObject("INSERT INTO deposit_transactions(reader_user_id,request_key,fingerprint,kind,method,total_vnd,actor_id,actor_name,fee_payment_id) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,reader,key,hash,kind,"DEDUCT".equals(kind)?"DEPOSIT_OFFSET":"CASH",total,actor,name,payment);
    }
    public void entry(Long transaction,Long account,long amount) { jdbc.update("INSERT INTO deposit_entries(transaction_id,account_id,amount_vnd) VALUES(?,?,?)",transaction,account,amount); }
    public Transaction transaction(Long id) {
        var entries=jdbc.query("SELECT account_id,amount_vnd FROM deposit_entries WHERE transaction_id=? ORDER BY account_id",(rs,n)->new Entry(rs.getLong(1),rs.getLong(2)),id);
        return jdbc.queryForObject("SELECT * FROM deposit_transactions WHERE id=?",(rs,n)->transactionRow(rs,entries),id);
    }
    private static Transaction transactionRow(java.sql.ResultSet rs,List<Entry> entries) throws java.sql.SQLException {
        return new Transaction(rs.getLong("id"),rs.getString("transaction_number"),rs.getLong("reader_user_id"),rs.getString("kind"),rs.getString("method"),rs.getLong("total_vnd"),rs.getLong("actor_id"),rs.getString("actor_name"),rs.getObject("recorded_at",OffsetDateTime.class),rs.getObject("fee_payment_id",Long.class),List.copyOf(entries));
    }
    public Transactions transactions(Long reader,int page) {
        var entries=new HashMap<Long,List<Entry>>();
        jdbc.query("""
            SELECT e.transaction_id,e.account_id,e.amount_vnd FROM deposit_entries e
            JOIN (SELECT id FROM deposit_transactions WHERE reader_user_id=? ORDER BY id DESC LIMIT 10 OFFSET ?) p ON p.id=e.transaction_id
            ORDER BY e.transaction_id,e.account_id
            """,(org.springframework.jdbc.core.RowCallbackHandler)rs->entries.computeIfAbsent(rs.getLong(1),key->new ArrayList<>()).add(new Entry(rs.getLong(2),rs.getLong(3))),reader,(long)page*10);
        var rows=jdbc.query("SELECT * FROM deposit_transactions WHERE reader_user_id=? ORDER BY id DESC LIMIT 10 OFFSET ?",
            (rs,n)->transactionRow(rs,entries.getOrDefault(rs.getLong("id"),List.of())),reader,(long)page*10);
        return new Transactions(rows,page,10,jdbc.queryForObject("SELECT count(*) FROM deposit_transactions WHERE reader_user_id=?",Long.class,reader));
    }
    public Set<Long> feeIds(Long item) { return new HashSet<>(jdbc.query("SELECT id FROM reader_fees WHERE return_item_id=? OR compensation_item_id=?",(rs,n)->rs.getLong(1),item,item)); }
    public Long offsetReceipt(com.duanttcsn5.library.dto.loan.FeeCollectionResponse.Reader reader,Long actor,String actorName,long amount,long debtAfter,UUID key,String hash) {
        return jdbc.queryForObject("INSERT INTO fee_payments(reader_user_id,reader_name,card_number,collected_by,collector_name,total_amount_vnd,request_key,debt_token,debt_after_vnd,payment_mode,funding_source) VALUES(?,?,?,?,?,?,?,?,?,'PARTIAL','DEPOSIT_OFFSET') RETURNING id",Long.class,reader.id(),reader.fullName(),reader.cardNumber(),actor,actorName,amount,key,hash,debtAfter);
    }
}
