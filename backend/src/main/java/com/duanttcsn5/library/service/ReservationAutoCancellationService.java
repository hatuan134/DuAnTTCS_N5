package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationAutoCancellationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationAutoCancellationService.class);
    public static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String OVERDUE_CANCELLATION_REASON = "Đã huỷ do quá hạn nhận";
    public static final String SYSTEM_ACTOR_NAME = "Hệ thống";

    private final BookReservationRepository reservations;
    private final BookCopyRepository copies;
    private final LibraryConfigurationService configuration;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryConfigurationService configuration) {
        this(reservations, copies, configuration, Clock.system(LIBRARY_ZONE));
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryConfigurationService configuration,
            Clock clock) {
        this.reservations = reservations;
        this.copies = copies;
        this.configuration = configuration;
        this.clock = clock;
    }

    /**
     * S3-06.1 AC: Chạy nền mỗi ngày một lần vào 0 giờ 30 theo giờ Asia/Ho_Chi_Minh.
     * Tự động phát hiện và huỷ các đơn đặt giữ đã quá 3 ngày mở cửa mà Bạn đọc không đến nhận.
     */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runDailyScheduledCheck() {
        log.info("Bắt đầu tiến trình kiểm tra và huỷ tự động đơn đặt giữ quá hạn nhận lúc 00:30...");
        List<AutoCancelledReservationResponse> results = processOverdueReservations();
        log.info("Hoàn tất tiến trình kiểm tra đơn đặt giữ: đã tự động huỷ {} đơn quá hạn.", results.size());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<AutoCancelledReservationResponse> processOverdueReservations() {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        return processOverdueReservationsAt(now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<AutoCancelledReservationResponse> processOverdueReservationsAt(OffsetDateTime checkTime) {
        List<BookReservation> candidateList = reservations.findReadyForPickup();
        List<AutoCancelledReservationResponse> cancelledList = new ArrayList<>();

        for (BookReservation candidate : candidateList) {
            if (!isOverdue(candidate, checkTime)) {
                continue;
            }

            // Acquire row lock to prevent race conditions with concurrent loans or manual cancellations
            BookReservation target = reservations.findForCancellation(candidate.getId()).orElse(null);
            if (target == null || !"READY_FOR_PICKUP".equals(target.getStatus())) {
                continue;
            }

            // Re-verify deadline under the locked row
            if (!isOverdue(target, checkTime)) {
                continue;
            }

            target.cancelBySystem(checkTime, OVERDUE_CANCELLATION_REASON);
            reservations.saveAndFlush(target);

            // S3-06.1: Chưa chuyển bản sao cho người tiếp theo -> giải phóng bản sao về AVAILABLE
            BookCopy copy = target.getBookCopy();
            if (copy != null) {
                BookCopy managedCopy = copies.findForStatusChange(copy.getId()).orElse(null);
                if (managedCopy != null && "HELD".equals(managedCopy.getStatus())) {
                    managedCopy.releaseReservationHold();
                    copies.saveAndFlush(managedCopy);
                }
            }

            cancelledList.add(new AutoCancelledReservationResponse(
                    target.getId(),
                    target.getBook().getId(),
                    target.getBook().getTitle(),
                    target.getReader().getId(),
                    target.getReader().getFullName(),
                    copy == null ? null : copy.getId(),
                    copy == null ? null : copy.getBarcode(),
                    target.getStatus(),
                    target.getReservedAt(),
                    target.getPickupDeadline(),
                    target.getCancelledAt(),
                    target.getCancelledByName(),
                    target.getCancellationReason()
            ));

            log.info("Đã tự động huỷ đơn đặt giữ #{} của bạn đọc {} do quá 3 ngày mở cửa (Hạn nhận: {}, Thời điểm huỷ: {}).",
                    target.getId(), target.getReader().getFullName(), target.getPickupDeadline(), target.getCancelledAt());
        }

        return cancelledList;
    }

    public boolean isOverdue(BookReservation reservation, OffsetDateTime checkTime) {
        if (reservation == null || !"READY_FOR_PICKUP".equals(reservation.getStatus())) {
            return false;
        }

        OffsetDateTime deadline = reservation.getPickupDeadline();
        if (deadline == null) {
            try {
                deadline = configuration.calculateReservationPickupDeadline(reservation.getReservedAt());
            } catch (Exception ex) {
                log.warn("Không thể tính hạn nhận cho đơn đặt giữ #{}: {}", reservation.getId(), ex.getMessage());
                return false;
            }
        }

        if (deadline == null) {
            return false;
        }

        return checkTime.isAfter(deadline);
    }
}
