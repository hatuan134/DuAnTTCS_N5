package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.CollectFullFeesRequest;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.Fee;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.Reader;
import com.duanttcsn5.library.dto.loan.FeeReceiptResponse;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.FeePaymentRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class FeePaymentService {
    private final FeePaymentRepository payments;
    private final UserRepository users;
    public FeePaymentService(FeePaymentRepository payments, UserRepository users) {
        this.payments = payments; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Reader> search(String raw, Long actorId) {
        requireLibrarian(actorId);
        String query = raw == null ? "" : raw.strip();
        if (query.isBlank() || query.length() > 255) throw error(HttpStatus.BAD_REQUEST,
                "INVALID_READER_SEARCH", "Nhập mã thẻ, mã bạn đọc, họ tên, email hoặc số điện thoại từ 1 đến 255 ký tự.");
        return payments.search(query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FeeCollectionResponse detail(Long readerId, Long actorId) {
        requireLibrarian(actorId); validateId(readerId);
        var reader = findReader(readerId, false);
        var fees = payments.outstanding(readerId, false);
        long total = total(fees);
        return new FeeCollectionResponse(reader, fees, total, total > 0, token(fees), payments.payments(readerId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public FeeReceiptResponse collect(Long readerId, CollectFullFeesRequest request, Long actorId) {
        User actor = requireLibrarian(actorId); validateId(readerId); validateRequest(request);
        try {
            var reader = findReader(readerId, true);
            var existing = payments.replay(readerId, request.requestKey());
            if (existing.isPresent()) {
                var replay = existing.get();
                if (!Objects.equals(replay.actorId(), actorId) || BigDecimal.valueOf(replay.totalVnd()).compareTo(request.expectedTotalVnd()) != 0
                        || !replay.debtToken().equals(request.debtToken())) throw error(HttpStatus.CONFLICT,
                        "PAYMENT_REQUEST_REUSED", "Mã xác nhận đã dùng cho một giao dịch khác. Vui lòng tra cứu lại.");
                return findReceipt(replay.paymentId());
            }
            var fees = payments.outstanding(readerId, true);
            long total = total(fees);
            if (total == 0) throw error(HttpStatus.CONFLICT, "NO_OUTSTANDING_FEES",
                    "Bạn đọc không còn nợ phí. Không tạo thêm giao dịch thu tiền.");
            if (BigDecimal.valueOf(total).compareTo(request.expectedTotalVnd()) != 0 || !token(fees).equals(request.debtToken())) {
                throw error(HttpStatus.CONFLICT, "FEE_DEBT_CHANGED",
                        "Khoản nợ đã thay đổi. Vui lòng tải lại, kiểm tra số tiền và xác nhận lại.");
            }
            Long id = payments.insert(reader, actor.getId(), actor.getFullName(), total, request.requestKey(), request.debtToken());
            for (var fee : fees) {
                payments.insertItem(id, fee);
                if (payments.settle(readerId, fee) != 1) throw error(HttpStatus.CONFLICT,
                        "FEE_DEBT_CHANGED", "Khoản phí đã thay đổi. Giao dịch được hủy, vui lòng tra cứu lại.");
            }
            // Debt is derived from reader_fees throughout lending/renewal. There is no
            // separate DEBT card status to reset (and no reason to unlock an expired card).
            if (!payments.outstanding(readerId, false).isEmpty()) throw error(HttpStatus.CONFLICT,
                    "FEE_DEBT_CHANGED", "Chưa thể thu đủ toàn bộ khoản nợ. Giao dịch được hủy, vui lòng tra cứu lại.");
            return findReceipt(id);
        } catch (DataAccessException exception) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "FEE_PAYMENT_FAILED",
                    "Không thể ghi nhận thu phí. Toàn bộ giao dịch đã được hủy; vui lòng thử lại.");
        }
    }

    @Transactional(readOnly = true)
    public FeeReceiptResponse receipt(Long id, Long actorId) {
        requireLibrarian(actorId); validateId(id); return findReceipt(id);
    }

    private Reader findReader(Long id, boolean lock) {
        return payments.reader(id, lock).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "READER_NOT_FOUND", "Không tìm thấy bạn đọc."));
    }
    private FeeReceiptResponse findReceipt(Long id) {
        return payments.receipt(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "FEE_RECEIPT_NOT_FOUND", "Không tìm thấy biên lai đã ghi nhận."));
    }
    private User requireLibrarian(Long id) {
        if (id == null) throw error(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập.");
        User actor = users.findById(id).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        if (!"ACTIVE".equals(actor.getStatus()) || actor.getRole() == null
                || !"LIBRARIAN".equals(actor.getRole().getCode())) throw error(HttpStatus.FORBIDDEN,
                "FEE_PAYMENT_ROLE_REQUIRED", "Chỉ Thủ thư được thu phí và xem, in lại biên lai.");
        return actor;
    }
    private void validateId(Long id) {
        if (id == null || id < 1) throw error(HttpStatus.BAD_REQUEST, "INVALID_ID", "Mã dữ liệu không hợp lệ.");
    }
    private void validateRequest(CollectFullFeesRequest r) {
        if (r == null || r.requestKey() == null || r.expectedTotalVnd() == null || r.expectedTotalVnd().signum() <= 0
                || r.expectedTotalVnd().stripTrailingZeros().scale() > 0
                || r.expectedTotalVnd().compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0
                || r.debtToken() == null || !r.debtToken().matches("[a-f0-9]{64}")) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_REQUEST", "Vui lòng tra cứu lại khoản nợ trước khi xác nhận thu đủ.");
        }
    }
    private long total(List<Fee> fees) {
        long result = 0;
        try {
            for (var fee : fees) result = Math.addExact(result, fee.outstandingAmountVnd());
        } catch (ArithmeticException e) { throw amountError(); }
        if (result > 9_007_199_254_740_991L) throw amountError();
        return result;
    }
    private ApiException amountError() {
        return error(HttpStatus.CONFLICT, "FEE_AMOUNT_TOO_LARGE", "Số tiền vượt giới hạn xử lý. Vui lòng kiểm tra dữ liệu khoản phí.");
    }
    private String token(List<Fee> fees) {
        var value = new StringBuilder();
        for (var f : fees) value.append(f.id()).append(':').append(f.originalAmountVnd()).append(':')
                .append(f.paidAmountVnd()).append(':').append(f.waivedAmountVnd()).append(';');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
