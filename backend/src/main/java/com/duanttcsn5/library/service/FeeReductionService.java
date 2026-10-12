package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.CreateFeeReductionRequest;
import com.duanttcsn5.library.dto.loan.FeeReductionRequestPageResponse;
import com.duanttcsn5.library.dto.loan.FeeReductionRequestResponse;
import com.duanttcsn5.library.dto.loan.OverdueFeeDetailResponse;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.OverdueFeeRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;

@Service
public class FeeReductionService {
    private static final Set<String> STAFF = Set.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN");
    private static final int PAGE_SIZE = 20;
    private final OverdueFeeRepository fees;
    private final UserRepository users;

    public FeeReductionService(OverdueFeeRepository fees, UserRepository users) {
        this.fees = fees;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public OverdueFeeDetailResponse detail(Long feeId, Long actorId) {
        requireRole(actorId, STAFF);
        validateId(feeId);
        var fee = findFee(feeId, false);
        return fee.withPendingRequest(fees.pendingReduction(feeId).orElse(null));
    }

    @Transactional
    public FeeReductionRequestResponse create(Long feeId, CreateFeeReductionRequest request, Long actorId) {
        User actor = requireRole(actorId, Set.of("LIBRARIAN"));
        validateId(feeId);
        // Lock the fee before checking payment state and pending requests.
        var fee = findFee(feeId, true);
        if (fee.originalAmountVnd() <= 0 || fee.paidAmountVnd() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FEE_REDUCTION_NOT_ALLOWED",
                    "Chỉ khoản phí trễ hạn lớn hơn 0 VND và chưa thu tiền mới được đề nghị miễn giảm.");
        }
        if (fees.pendingReduction(feeId).isPresent()) throw pendingConflict();
        String reason = trimReason(request == null ? null : request.reason());
        if (reason.isBlank() || reason.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDUCTION_REASON",
                    "Lý do phải có nội dung và không vượt quá 1000 ký tự.");
        }
        BigDecimal amount = request.amountVnd();
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(BigDecimal.valueOf(fee.originalAmountVnd())) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDUCTION_AMOUNT",
                    "Số tiền đề nghị phải là số nguyên VND, lớn hơn 0 và không vượt quá phí gốc.");
        }
        try {
            return fees.insertReduction(fee, actor.getId(), actor.getFullName(), amount.longValueExact(), reason);
        } catch (DuplicateKeyException exception) {
            // The partial unique index also protects requests from concurrent callers.
            throw pendingConflict();
        }
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public FeeReductionRequestPageResponse pending(int page, Long actorId) {
        requireRole(actorId, Set.of("LIBRARY_MANAGER"));
        if (page < 0 || page > 1_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Trang danh sách không hợp lệ.");
        }
        long total = fees.countPendingReductions();
        int pages = (int) Math.min(Integer.MAX_VALUE, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, Math.max(0, pages - 1));
        return new FeeReductionRequestPageResponse(fees.pendingReductions(PAGE_SIZE, (long) actualPage * PAGE_SIZE),
                actualPage, PAGE_SIZE, total, pages);
    }

    private OverdueFeeRepository.FeeRow findFee(Long id, boolean lock) {
        return fees.findOverdueFee(id, lock).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "OVERDUE_FEE_NOT_FOUND", "Không tìm thấy khoản phí trễ hạn đã ghi nhận khi nhận trả."));
    }

    private void validateId(Long id) {
        if (id == null || id < 1) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_FEE_ID", "Mã khoản phí không hợp lệ.");
    }

    /** Includes non-breaking spaces used by pasted text, matching browser trimming. */
    private String trimReason(String value) {
        if (value == null) return "";
        int start = 0, end = value.length();
        while (start < end && isReasonSpace(value.charAt(start))) start++;
        while (end > start && isReasonSpace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private boolean isReasonSpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value) || value == '\uFEFF';
    }

    private ApiException pendingConflict() {
        return new ApiException(HttpStatus.CONFLICT, "FEE_REDUCTION_PENDING",
                "Khoản phí đã có đề nghị Chờ duyệt. Vui lòng chờ Quản lý thư viện xử lý.");
    }

    private User requireRole(Long actorId, Set<String> roles) {
        if (actorId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập.");
        User actor = users.findById(actorId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        String role = actor.getRole() == null ? null : actor.getRole().getCode();
        if (role == null || !roles.contains(role) || !"ACTIVE".equals(actor.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEE_REDUCTION_ROLE_REQUIRED",
                    "Bạn không có quyền thực hiện thao tác miễn giảm phí này.");
        }
        return actor;
    }
}
