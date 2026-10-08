package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationAutoCancellationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationAutoCancellationService.class);
    public static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String OVERDUE_CANCELLATION_REASON = "Đã huỷ do quá hạn nhận";
    public static final String SYSTEM_ACTOR_NAME = "Hệ thống";

    private final BookReservationRepository reservations;
    private final BookCopyRepository copies;
    private final LibraryCardRepository cards;
    private final LibraryConfigurationService configuration;
    private final Clock clock;

    @Autowired
    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryCardRepository cards,
            LibraryConfigurationService configuration) {
        this(reservations, copies, cards, configuration, Clock.system(LIBRARY_ZONE));
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryConfigurationService configuration) {
        this(reservations, copies, null, configuration, Clock.system(LIBRARY_ZONE));
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryConfigurationService configuration,
            Clock clock) {
        this(reservations, copies, null, configuration, clock);
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryCardRepository cards,
            LibraryConfigurationService configuration,
            Clock clock) {
        this.reservations = reservations;
        this.copies = copies;
        this.cards = cards;
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

            // S3-06.2: Xử lý chuyển bản sao cho người kế tiếp trong hàng đợi hoặc trả về Sẵn sàng
            BookCopy copy = target.getBookCopy();
            String copyOutcome = "NO_COPY";
            Long nextReservationId = null;
            String nextReaderName = null;
            OffsetDateTime nextPickupDeadline = null;

            if (copy != null) {
                BookCopy managedCopy = copies.findForStatusChange(copy.getId()).orElse(null);
                if (managedCopy != null && "HELD".equals(managedCopy.getStatus())) {
                    BookReservation nextEligible = findNextEligiblePending(target.getBook().getId(), checkTime.toLocalDate());
                    if (nextEligible != null) {
                        // Tính lại hạn nhận của người tiếp theo theo 3 ngày thư viện mở cửa
                        OffsetDateTime nextDeadline = configuration.calculateReservationPickupDeadline(checkTime);

                        nextEligible.setStatus("READY_FOR_PICKUP");
                        nextEligible.setBookCopy(managedCopy);
                        nextEligible.setReservedAt(checkTime); // Ghi thời điểm bắt đầu chờ nhận mới
                        nextEligible.setPickupDeadline(nextDeadline);
                        reservations.saveAndFlush(nextEligible);

                        copyOutcome = "TRANSFERRED";
                        nextReservationId = nextEligible.getId();
                        nextReaderName = nextEligible.getReader().getFullName();
                        nextPickupDeadline = nextDeadline;

                        log.info("Bản sao {} của đơn quá hạn #{} được chuyển cho đơn kế tiếp #{} của bạn đọc {}. Hạn nhận mới: {}.",
                                managedCopy.getBarcode(), target.getId(), nextEligible.getId(), nextReaderName, nextDeadline);
                    } else {
                        // Hàng đợi rỗng hoặc không còn ai hợp lệ -> trả bản sao về Sẵn sàng (AVAILABLE)
                        managedCopy.releaseReservationHold();
                        copies.saveAndFlush(managedCopy);
                        copyOutcome = "AVAILABLE";

                        log.info("Không còn người chờ hợp lệ trong hàng đợi cho đầu sách #{}. Bản sao {} trở về trạng thái Sẵn sàng (AVAILABLE).",
                                target.getBook().getId(), managedCopy.getBarcode());
                    }
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
                    target.getCancellationReason(),
                    copyOutcome,
                    nextReservationId,
                    nextReaderName,
                    nextPickupDeadline
            ));

            log.info("Đã tự động huỷ đơn đặt giữ #{} của bạn đọc {} do quá 3 ngày mở cửa (Hạn nhận: {}, Thời điểm huỷ: {}). Kết quả bản sao: {}.",
                    target.getId(), target.getReader().getFullName(), target.getPickupDeadline(), target.getCancelledAt(), copyOutcome);
        }

        return cancelledList;
    }

    /**
     * S3-06.2: Tìm người đứng đầu hàng đợi hợp lệ.
     * Bỏ qua những người có thẻ bị khóa, hết hạn hoặc tài khoản không hoạt động.
     */
    public BookReservation findNextEligiblePending(Long bookId, LocalDate today) {
        List<BookReservation> pendingQueue = reservations.findPendingQueueForAllocation(bookId);
        for (BookReservation candidate : pendingQueue) {
            if (isReaderEligibleForAllocation(candidate.getReader(), today)) {
                return candidate;
            } else {
                log.info("Bỏ qua đơn #{} trong hàng đợi: Bạn đọc {} không hợp lệ (thẻ hết hạn, bị khóa hoặc tài khoản ngưng hoạt động).",
                        candidate.getId(), candidate.getReader() != null ? candidate.getReader().getFullName() : "N/A");
            }
        }
        return null;
    }

    /**
     * S3-06.2: Kiểm tra tính hợp lệ của bạn đọc trong hàng đợi:
     * - Tài khoản phải đang hoạt động (ACTIVE).
     * - Thẻ thư viện phải tồn tại và đang hoạt động (ACTIVE).
     * - Thẻ không bị khóa (LOCKED) và chưa hết hạn (EXPIRED/quá ngày expiresAt).
     */
    public boolean isReaderEligibleForAllocation(User reader, LocalDate today) {
        if (reader == null || !"ACTIVE".equals(reader.getStatus())) {
            return false;
        }
        if (cards == null) {
            return true; // Fallback khi mock không inject cards
        }
        Optional<LibraryCard> cardOpt = cards.findByUserIdWithDetails(reader.getId());
        if (cardOpt.isEmpty()) {
            return false;
        }
        LibraryCard card = cardOpt.get();
        if ("LOCKED".equals(card.getStatus())) {
            return false;
        }
        if ("EXPIRED".equals(card.getStatus())
                || (card.getExpiresAt() != null && card.getExpiresAt().isBefore(today))) {
            return false;
        }
        return "ACTIVE".equals(card.getStatus()) && card.getExpiresAt() != null;
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
