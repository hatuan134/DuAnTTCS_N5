package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.dto.book.AutoCancellationRunResponse;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.ReservationAutoCancellationRun;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.ReservationAutoCancellationRunRepository;
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
    private final ReservationAutoCancellationRunRepository runRepository;
    private final LibraryConfigurationService configuration;
    private final Clock clock;

    @Autowired
    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryCardRepository cards,
            ReservationAutoCancellationRunRepository runRepository,
            LibraryConfigurationService configuration) {
        this(reservations, copies, cards, runRepository, configuration, Clock.system(LIBRARY_ZONE));
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryConfigurationService configuration) {
        this(reservations, copies, null, null, configuration, Clock.system(LIBRARY_ZONE));
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryCardRepository cards,
            LibraryConfigurationService configuration) {
        this(reservations, copies, cards, null, configuration, Clock.system(LIBRARY_ZONE));
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryConfigurationService configuration,
            Clock clock) {
        this(reservations, copies, null, null, configuration, clock);
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryCardRepository cards,
            LibraryConfigurationService configuration,
            Clock clock) {
        this(reservations, copies, cards, null, configuration, clock);
    }

    public ReservationAutoCancellationService(
            BookReservationRepository reservations,
            BookCopyRepository copies,
            LibraryCardRepository cards,
            ReservationAutoCancellationRunRepository runRepository,
            LibraryConfigurationService configuration,
            Clock clock) {
        this.reservations = reservations;
        this.copies = copies;
        this.cards = cards;
        this.runRepository = runRepository;
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
        AutoCancellationRunResponse runResponse = executeAutoCancellationRun(
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS), "SYSTEM");
        log.info("Hoàn tất tiến trình kiểm tra đơn đặt giữ: đã tự động huỷ {} đơn quá hạn.",
                runResponse.getTotalCancelled());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<AutoCancelledReservationResponse> processOverdueReservations() {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        return processOverdueReservationsAt(now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<AutoCancelledReservationResponse> processOverdueReservationsAt(OffsetDateTime checkTime) {
        return executeAutoCancellationRun(checkTime, "MANUAL_TRIGGER").getCancelledReservations();
    }

    /**
     * S3-06.3: Ghi nhận từng lần chạy tự động và bảo đảm chạy lại không làm huỷ hoặc chuyển bản sao sai lần thứ hai.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AutoCancellationRunResponse executeAutoCancellationRun(OffsetDateTime checkTime, String triggeredBy) {
        LocalDate runDate = checkTime.toLocalDate();
        OffsetDateTime startedAt = checkTime;

        List<BookReservation> candidateList = reservations.findReadyForPickup();
        List<AutoCancelledReservationResponse> cancelledDetails = new ArrayList<>();

        int totalIdentified = 0;
        int totalCancelled = 0;
        int totalTransferred = 0;
        int totalReleased = 0;
        int errorCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        // 1. Lọc các đơn quá hạn hợp lệ, bỏ qua các đơn không còn Chờ nhận hoặc đã xử lý trước đó
        List<BookReservation> overdueCandidates = new ArrayList<>();
        for (BookReservation candidate : candidateList) {
            if (candidate == null
                    || !"READY_FOR_PICKUP".equals(candidate.getStatus())
                    || candidate.getAutoCancellationRun() != null) {
                continue;
            }
            if (isOverdue(candidate, checkTime)) {
                overdueCandidates.add(candidate);
            }
        }

        totalIdentified = overdueCandidates.size();

        // 2. Tạo bản ghi ban đầu cho lần chạy
        ReservationAutoCancellationRun runRecord = new ReservationAutoCancellationRun(
                runDate,
                startedAt,
                startedAt,
                "SUCCESS",
                totalIdentified,
                0, 0, 0, 0, null,
                triggeredBy != null ? triggeredBy : "SYSTEM"
        );
        if (runRepository != null) {
            runRecord = runRepository.saveAndFlush(runRecord);
        }

        // 3. Xử lý từng đơn quá hạn với try-catch riêng để đảm bảo hỗ trợ Partial Failure (thất bại một phần)
        for (BookReservation candidate : overdueCandidates) {
            try {
                // Khóa dòng bi quan để tránh xung đột
                BookReservation target = reservations.findForCancellation(candidate.getId()).orElse(null);
                if (target == null
                        || !"READY_FOR_PICKUP".equals(target.getStatus())
                        || target.getAutoCancellationRun() != null) {
                    // Đơn đã được xử lý hoặc thay đổi trạng thái bởi giao dịch khác -> bỏ qua
                    continue;
                }

                if (!isOverdue(target, checkTime)) {
                    continue;
                }

                target.cancelBySystem(checkTime, OVERDUE_CANCELLATION_REASON);
                if (runRepository != null) {
                    target.setAutoCancellationRun(runRecord);
                }
                reservations.saveAndFlush(target);
                totalCancelled++;

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
                            totalTransferred++;
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
                            totalReleased++;

                            log.info("Không còn người chờ hợp lệ trong hàng đợi cho đầu sách #{}. Bản sao {} trở về trạng thái Sẵn sàng (AVAILABLE).",
                                    target.getBook().getId(), managedCopy.getBarcode());
                        }
                    }
                }

                cancelledDetails.add(new AutoCancelledReservationResponse(
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

            } catch (Exception ex) {
                log.error("Lỗi khi xử lý đơn quá hạn #{}: {}", candidate.getId(), ex.getMessage(), ex);
                errorCount++;
                if (!errorMessages.isEmpty()) {
                    errorMessages.append("; ");
                }
                errorMessages.append("Đơn #").append(candidate.getId()).append(": ").append(ex.getMessage());
            }
        }

        // 4. Cập nhật kết quả lần chạy
        OffsetDateTime finishedAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        String runStatus = "SUCCESS";
        if (errorCount > 0) {
            runStatus = totalCancelled > 0 ? "PARTIAL_FAILURE" : "FAILED";
        }

        runRecord.setFinishedAt(finishedAt);
        runRecord.setStatus(runStatus);
        runRecord.setTotalCancelled(totalCancelled);
        runRecord.setTotalTransferred(totalTransferred);
        runRecord.setTotalReleased(totalReleased);
        runRecord.setErrorCount(errorCount);
        if (!errorMessages.isEmpty()) {
            runRecord.setErrorMessage(errorMessages.toString());
        }

        if (runRepository != null) {
            runRecord = runRepository.saveAndFlush(runRecord);
        }

        log.info("Hoàn tất lần chạy [Run #{}]. Trạng thái: {}, Quá hạn: {}, Đã huỷ: {}, Chuyển bản sao: {}, Sẵn sàng: {}, Lỗi: {}.",
                runRecord.getId(), runStatus, totalIdentified, totalCancelled, totalTransferred, totalReleased, errorCount);

        return AutoCancellationRunResponse.fromEntity(runRecord, cancelledDetails);
    }

    /**
     * S3-06.3: Hiển thị kết quả của lần chạy gần nhất cho Quản lý kiểm tra.
     */
    public Optional<AutoCancellationRunResponse> getLatestRun() {
        if (runRepository == null) {
            return Optional.empty();
        }
        return runRepository.findFirstByOrderByStartedAtDesc()
                .map(run -> AutoCancellationRunResponse.fromEntity(run, List.of()));
    }

    /**
     * S3-06.3: Lấy danh sách lịch sử tất cả các lần chạy tự động.
     */
    public List<AutoCancellationRunResponse> getAllRuns() {
        if (runRepository == null) {
            return List.of();
        }
        return runRepository.findAllByOrderByStartedAtDesc().stream()
                .map(run -> AutoCancellationRunResponse.fromEntity(run, List.of()))
                .toList();
    }

    /**
     * S3-06.4: Quản lý tra cứu các đơn đã bị hệ thống tự động huỷ trong 30 ngày gần nhất.
     * Mốc 30 ngày được tính theo 30 ngày lịch (Calendar days) từ thời điểm hiện tại.
     * Chỉ lấy các đơn do quy trình tự động huỷ (quá hạn nhận, Huỷ bởi Hệ thống).
     * Sắp xếp các đơn bị huỷ gần nhất lên trước.
     */
    @Transactional(readOnly = true)
    public List<AutoCancelledReservationResponse> getAutoCancelledReservationsLast30Days() {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime cutoff = now.minusDays(30);
        return getAutoCancelledReservationsSince(cutoff);
    }

    @Transactional(readOnly = true)
    public List<AutoCancelledReservationResponse> getAutoCancelledReservationsSince(OffsetDateTime sinceTime) {
        List<BookReservation> cancelledList = reservations.findAutoCancelledReservationsSince(sinceTime);
        List<AutoCancelledReservationResponse> responses = new ArrayList<>();

        for (BookReservation r : cancelledList) {
            BookCopy copy = r.getBookCopy();
            String copyOutcome = "NO_COPY";
            Long nextReservationId = null;
            String nextReaderName = null;
            OffsetDateTime nextPickupDeadline = null;

            if (copy != null) {
                // Kiểm tra xem bản sao này sau khi huỷ có được gán cho đơn kế tiếp nào không
                try {
                    List<BookReservation> queueReservations = reservations.findAllForQueueByBookId(r.getBook().getId());
                    Optional<BookReservation> successorOpt = queueReservations.stream()
                            .filter(s -> s.getBookCopy() != null
                                    && s.getBookCopy().getId().equals(copy.getId())
                                    && !s.getId().equals(r.getId())
                                    && (s.getReservedAt() != null && !s.getReservedAt().isBefore(r.getCancelledAt())))
                            .findFirst();

                    if (successorOpt.isPresent()) {
                        BookReservation next = successorOpt.get();
                        copyOutcome = "TRANSFERRED";
                        nextReservationId = next.getId();
                        nextReaderName = next.getReader() != null ? next.getReader().getFullName() : null;
                        nextPickupDeadline = next.getPickupDeadline();
                    } else {
                        copyOutcome = "AVAILABLE";
                    }
                } catch (Exception ex) {
                    copyOutcome = "AVAILABLE";
                }
            }

            responses.add(new AutoCancelledReservationResponse(
                    r.getId(),
                    r.getBook().getId(),
                    r.getBook().getTitle(),
                    r.getReader().getId(),
                    r.getReader().getFullName(),
                    copy == null ? null : copy.getId(),
                    copy == null ? null : copy.getBarcode(),
                    r.getStatus(),
                    r.getReservedAt(),
                    r.getPickupDeadline(),
                    r.getCancelledAt(),
                    r.getCancelledByName(),
                    r.getCancellationReason(),
                    copyOutcome,
                    nextReservationId,
                    nextReaderName,
                    nextPickupDeadline
            ));
        }

        return responses;
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
