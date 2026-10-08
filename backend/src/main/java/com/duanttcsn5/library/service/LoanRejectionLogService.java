package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.LoanRejectionResponse;
import com.duanttcsn5.library.dto.loan.LoanRejectionPageResponse;
import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRejectionRepository;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class LoanRejectionLogService {
    private final LoanRejectionRepository repository;
    private final LoanRepository loans;
    private final UserRepository users;

    public LoanRejectionLogService(LoanRejectionRepository repository, LoanRepository loans, UserRepository users) {
        this.repository = repository;
        this.loans = loans;
        this.users = users;
    }

    /** A separate transaction commits the rejection even if confirmation throws HTTP 409.
     *  Snapshot rows deliberately have no foreign keys to reader/card/actor: an outer
     *  transaction may hold their row locks when the rejection is recorded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID requestId, String source, Long readerId, String readerName,
                    String cardNumber, Long actorId, Long reservationId,
                    long borrowed, int maximum, List<LoanRejectionResponse.Reason> reasons) {
        if (requestId == null || readerId == null || actorId == null || reasons == null || reasons.isEmpty()) return;
        String actorName = users.findById(actorId).map(u -> u.getFullName()).orElse("Nhân viên #" + actorId);
        long overdue = loans.countOverdueUnreturnedLoansForReader(readerId,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        BigDecimal unpaid = loans.sumUnpaidFeesForReader(readerId);
        repository.save(requestId, source, readerId, readerName == null ? "Bạn đọc #" + readerId : readerName,
                cardNumber, actorId, actorName == null ? "Nhân viên #" + actorId : actorName,
                reservationId, borrowed, maximum, overdue, unpaid == null ? BigDecimal.ZERO : unpaid, reasons);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEligibility(UUID requestId, String source, Long actorId,
                               ReaderLoanEligibilityResponse eligibility) {
        if (eligibility.eligible()) return;
        log(requestId, source, eligibility.readerId(), eligibility.readerName(),
                eligibility.cardNumber(), actorId, null, eligibility.borrowedBooks(),
                eligibility.maxBooks(), eligibility.blockReasons().stream()
                        .map(reason -> new LoanRejectionResponse.Reason(reason.code(), reason.message())).toList());
    }

    /** Audit and loan MUST commit or roll back together (never REQUIRES_NEW here). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void logOverride(UUID requestId, String source, Long readerId, String readerName,
            String cardNumber, Long actorId, Long reservationId, long borrowed, int maximum,
            Long loanId, String reason, List<LoanRejectionResponse.Reason> violations) {
        String actorName = users.findById(actorId).map(u -> u.getFullName()).orElse("Quản lý #" + actorId);
        long overdue = loans.countOverdueUnreturnedLoansForReader(readerId,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        BigDecimal unpaid = loans.sumUnpaidFeesForReader(readerId);
        repository.saveOverride(requestId, source, readerId, readerName, cardNumber, actorId,
                actorName == null ? "Quản lý #" + actorId : actorName, reservationId,
                borrowed, maximum, overdue, unpaid == null ? BigDecimal.ZERO : unpaid,
                loanId, reason, violations);
    }

    @Transactional(readOnly = true)
    public LoanRejectionPageResponse page(String cardNumber, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (cardNumber != null && cardNumber.length() > 100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_FILTER", "Bộ lọc nhật ký không hợp lệ.");
        }
        return repository.page(cardNumber, page, size);
    }

    @Transactional(readOnly = true)
    public LoanRejectionResponse detail(Long id) {
        if (id == null || id < 1) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_REJECTION_ID", "Mã nhật ký không hợp lệ.");
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "LOAN_REJECTION_NOT_FOUND", "Không tìm thấy nhật ký từ chối cho mượn."));
    }
}
