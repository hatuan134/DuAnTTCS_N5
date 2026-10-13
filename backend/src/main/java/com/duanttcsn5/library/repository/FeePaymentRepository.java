package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.Fee;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.Reader;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.PaymentSummary;
import com.duanttcsn5.library.dto.loan.FeeReceiptResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Monetary transactions use JDBC, as do the existing loan and reader-fee repositories. */
@Repository
public class FeePaymentRepository {
    private final JdbcTemplate jdbc;
    public FeePaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String READER_SELECT = """
            SELECT u.id, u.full_name, u.email, u.phone, p.member_code, c.card_number, c.status AS card_status
            FROM users u JOIN roles r ON r.id = u.role_id
            LEFT JOIN reader_profiles p ON p.user_id = u.id
            LEFT JOIN library_cards c ON c.user_id = u.id
            WHERE r.code = 'READER'
            """;
    private static final RowMapper<Reader> READER_MAPPER = (rs, n) -> new Reader(rs.getLong("id"),
            rs.getString("full_name"), rs.getString("card_number"), rs.getString("member_code"),
            rs.getString("email"), rs.getString("phone"), rs.getString("card_status"));

    public List<Reader> search(String query) {
        // Literal substring search: '%' and '_' in input are not SQL wildcards.
        return jdbc.query(READER_SELECT + """
                AND (strpos(lower(coalesce(c.card_number, '')), lower(?)) > 0
                  OR strpos(lower(coalesce(p.member_code, '')), lower(?)) > 0
                  OR strpos(lower(u.full_name), lower(?)) > 0
                  OR strpos(lower(u.email), lower(?)) > 0
                  OR strpos(coalesce(u.phone, ''), ?) > 0)
                ORDER BY u.full_name, u.id LIMIT 21
                """, READER_MAPPER, query, query, query, query, query);
    }

    public Optional<Reader> reader(Long id, boolean lock) {
        // NO KEY UPDATE serializes collectors/new fee inserts without blocking FK checks.
        return jdbc.query(READER_SELECT + " AND u.id = ?" + (lock ? " FOR NO KEY UPDATE OF u" : ""),
                READER_MAPPER, id).stream().findFirst();
    }

    public List<Fee> outstanding(Long readerId, boolean lock) {
        // Lock every existing fee in ID order, then filter in Java. A concurrently paid fee
        // or approved waiver is re-read after waiting at READ COMMITTED isolation.
        var all = jdbc.query("""
                SELECT id, CASE WHEN compensation_item_id IS NOT NULL THEN 'COMPENSATION' WHEN return_item_id IS NULL THEN 'RECORDED' ELSE 'OVERDUE' END AS fee_type,
                       description, amount_vnd, paid_amount_vnd, waived_amount_vnd
                FROM reader_fees WHERE reader_user_id = ? ORDER BY id
                """ + (lock ? " FOR UPDATE" : ""), (rs, n) -> new Fee(rs.getLong("id"),
                rs.getString("fee_type"), rs.getString("description"), rs.getLong("amount_vnd"),
                rs.getLong("paid_amount_vnd"), rs.getLong("waived_amount_vnd"),
                rs.getLong("amount_vnd") - rs.getLong("paid_amount_vnd") - rs.getLong("waived_amount_vnd")), readerId);
        return all.stream().filter(f -> f.outstandingAmountVnd() > 0).toList();
    }

    public record Replay(Long paymentId, Long actorId, long totalVnd, String debtToken,
                         long debtBeforeVnd, String paymentMode) {
        public Replay(Long paymentId, Long actorId, long totalVnd, String debtToken) {
            this(paymentId, actorId, totalVnd, debtToken, totalVnd, "FULL");
        }
    }
    public Optional<Replay> replay(Long readerId, UUID key) {
        return jdbc.query("SELECT id, collected_by, total_amount_vnd, debt_token, debt_before_vnd, payment_mode FROM fee_payments WHERE reader_user_id = ? AND request_key = ?",
                (rs, n) -> new Replay(rs.getLong("id"), rs.getObject("collected_by", Long.class),
                        rs.getLong("total_amount_vnd"), rs.getString("debt_token"),
                        rs.getLong("debt_before_vnd"), rs.getString("payment_mode")), readerId, key).stream().findFirst();
    }

    public Long insert(Reader reader, Long actorId, String actorName, long total, UUID key, String token) {
        return insert(reader, actorId, actorName, total, key, token, 0, "FULL");
    }

    public Long insert(Reader reader, Long actorId, String actorName, long amount, UUID key, String token,
                       long debtAfter, String mode) {
        return jdbc.queryForObject("""
                INSERT INTO fee_payments(reader_user_id, reader_name, card_number, collected_by,
                    collector_name, total_amount_vnd, request_key, debt_token, debt_after_vnd, payment_mode)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, reader.id(), reader.fullName(), reader.cardNumber(), actorId, actorName,
                amount, key, token, debtAfter, mode);
    }

    public void insertItem(Long paymentId, Fee fee) {
        insertItem(paymentId, fee, fee.outstandingAmountVnd());
    }

    public void insertItem(Long paymentId, Fee fee, long amount) {
        jdbc.update("""
                INSERT INTO fee_payment_items(payment_id, reader_fee_id, fee_type, description,
                    original_amount_vnd, previously_paid_vnd, waived_amount_vnd, collected_amount_vnd)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, paymentId, fee.id(), fee.feeType(), fee.description(), fee.originalAmountVnd(),
                fee.paidAmountVnd(), fee.waivedAmountVnd(), amount);
    }

    public int settle(Long readerId, Fee fee) {
        return jdbc.update("""
                UPDATE reader_fees SET paid_amount_vnd = amount_vnd - waived_amount_vnd
                WHERE id = ? AND reader_user_id = ? AND amount_vnd = ? AND paid_amount_vnd = ? AND waived_amount_vnd = ?
                """, fee.id(), readerId, fee.originalAmountVnd(), fee.paidAmountVnd(), fee.waivedAmountVnd());
    }

    public int applyPayment(Long readerId, Fee fee, long amount) {
        return jdbc.update("""
                UPDATE reader_fees SET paid_amount_vnd = paid_amount_vnd + ?
                WHERE id = ? AND reader_user_id = ? AND amount_vnd = ? AND paid_amount_vnd = ?
                  AND waived_amount_vnd = ? AND ? > 0 AND ? <= amount_vnd - paid_amount_vnd - waived_amount_vnd
                """, amount, fee.id(), readerId, fee.originalAmountVnd(), fee.paidAmountVnd(),
                fee.waivedAmountVnd(), amount, amount);
    }

    public List<PaymentSummary> payments(Long readerId) {
        return jdbc.query("""
                SELECT id, transaction_number, receipt_number, collected_at, total_amount_vnd, collector_name, debt_after_vnd
                FROM fee_payments WHERE reader_user_id = ? ORDER BY collected_at DESC, id DESC
                """, (rs, n) -> new PaymentSummary(rs.getLong("id"), rs.getString("transaction_number"),
                rs.getString("receipt_number"), rs.getObject("collected_at", OffsetDateTime.class),
                rs.getLong("total_amount_vnd"), rs.getString("collector_name"), rs.getLong("debt_after_vnd")), readerId);
    }

    public Optional<FeeReceiptResponse> receipt(Long id) {
        return jdbc.query("SELECT * FROM fee_payments WHERE id = ?", (rs, n) -> new FeeReceiptResponse(
                rs.getLong("id"), rs.getString("transaction_number"), rs.getString("receipt_number"),
                rs.getLong("reader_user_id"), rs.getString("reader_name"), rs.getString("card_number"),
                rs.getObject("collected_by", Long.class), rs.getString("collector_name"),
                rs.getObject("collected_at", OffsetDateTime.class), rs.getLong("total_amount_vnd"),
                rs.getLong("debt_before_vnd"), rs.getLong("debt_after_vnd"), rs.getString("payment_mode"), items(id)), id)
                .stream().findFirst();
    }

    private List<FeeReceiptResponse.Item> items(Long id) {
        return jdbc.query("SELECT * FROM fee_payment_items WHERE payment_id = ? ORDER BY reader_fee_id",
                (rs, n) -> new FeeReceiptResponse.Item(rs.getLong("reader_fee_id"), rs.getString("fee_type"),
                rs.getString("description"), rs.getLong("original_amount_vnd"), rs.getLong("previously_paid_vnd"),
                rs.getLong("waived_amount_vnd"), rs.getLong("collected_amount_vnd")), id);
    }
}
