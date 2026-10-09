package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.CreateOverdueContactRequest;
import com.duanttcsn5.library.dto.loan.OverdueContactResponse;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.OverdueContactRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class OverdueContactService {
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final OverdueContactRepository contacts;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public OverdueContactService(OverdueContactRepository contacts, UserRepository users) {
        this(contacts, users, Clock.system(LIBRARY_ZONE));
    }

    // Injectable clock keeps boundary and timestamp tests deterministic.
    public OverdueContactService(OverdueContactRepository contacts, UserRepository users, Clock clock) {
        this.contacts = contacts;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public OverdueContactResponse record(Long loanId, CreateOverdueContactRequest request, Long actorId) {
        User actor = requireStaff(actorId, true);
        validateId(loanId);
        String note = request == null || request.note() == null ? "" : request.note().trim();
        if (note.isEmpty() || note.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTACT_NOTE",
                    "Ghi chú liên hệ phải có nội dung và không vượt quá 1000 ký tự.");
        }
        OffsetDateTime at = OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        OffsetDateTime todayStart = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE)
                .atStartOfDay(LIBRARY_ZONE).toOffsetDateTime();
        if (!contacts.lockOpenOverdueLoan(loanId, todayStart)) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_NOT_OVERDUE",
                    "Phiếu không tồn tại hoặc không còn sách đang quá hạn để ghi nhận liên hệ.");
        }
        return contacts.insert(loanId, actor.getId(), actor.getFullName(), note, at);
    }

    @Transactional(readOnly = true)
    public List<OverdueContactResponse> history(Long loanId, Long actorId) {
        requireStaff(actorId, false);
        validateId(loanId);
        if (!contacts.loanExists(loanId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Không tìm thấy phiếu mượn.");
        }
        return contacts.history(loanId);
    }

    private void validateId(Long id) {
        if (id == null || id < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_ID", "Mã phiếu mượn không hợp lệ.");
        }
    }

    private User requireStaff(Long actorId, boolean write) {
        if (actorId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "Vui lòng đăng nhập.");
        }
        User actor = users.findById(actorId).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ."));
        String role = actor.getRole() == null ? null : actor.getRole().getCode();
        boolean authorized = write ? "LIBRARIAN".equals(role)
                : (role != null && Set.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN").contains(role));
        if (!authorized || !"ACTIVE".equals(actor.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAFF_ROLE_REQUIRED", write
                    ? "Chỉ Thủ thư đang hoạt động mới được ghi nhận liên hệ."
                    : "Bạn không có quyền xem lịch sử liên hệ.");
        }
        return actor;
    }
}
