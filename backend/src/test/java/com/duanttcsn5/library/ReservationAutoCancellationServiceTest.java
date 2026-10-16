package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.ReservationAutoCancellationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationAutoCancellationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BookReservationRepository reservations;
    private BookCopyRepository copies;
    private LibraryConfigurationService calendar;
    private ReservationAutoCancellationService service;

    private Book book;
    private User reader;

    @BeforeEach
    void setUp() {
        reservations = mock(BookReservationRepository.class);
        copies = mock(BookCopyRepository.class);
        calendar = mock(LibraryConfigurationService.class);
        service = new ReservationAutoCancellationService(reservations, copies, calendar, Clock.system(ZONE));

        book = new Book();
        book.setId(10L);
        book.setTitle("Lập trình Java căn bản");

        Role role = new Role();
        role.setCode("READER");
        reader = new User();
        reader.setId(100L);
        reader.setFullName("Nguyễn Văn An");
        reader.setRole(role);
        reader.setStatus("ACTIVE");
    }

    private BookCopy createHeldCopy(long id, String barcode) {
        BookCopy copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "barcode", barcode);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "status", "HELD");
        return copy;
    }

    private BookReservation createReservation(long id, String status, OffsetDateTime reservedAt, OffsetDateTime deadline, BookCopy copy) {
        BookReservation r = new BookReservation(book, reader, status);
        r.setId(id);
        r.setReservedAt(reservedAt);
        r.setPickupDeadline(deadline);
        r.setBookCopy(copy);
        return r;
    }

    @Test
    @DisplayName("Đơn mới chờ 1 ngày mở cửa -> KHÔNG bị huỷ (vẫn giữ nguyên)")
    void waitingOneOpenDay_shouldNotBeCancelled() {
        // Thứ 2 lúc 09:00 bắt đầu chờ nhận -> Hạn 3 ngày mở cửa (Thứ 3, Thứ 4, Thứ 5) kết thúc lúc 17:00 Thứ 5
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation reservation = createReservation(1L, "READY_FOR_PICKUP", reservedAt, deadline, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));

        // Kiểm tra lúc 00:30 Thứ Tư (mới chỉ qua 1 ngày mở cửa là Thứ Ba)
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-07T00:30:00+07:00");
        List<AutoCancelledReservationResponse> result = service.processOverdueReservationsAt(checkTime);

        assertThat(result).isEmpty();
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(reservation.getCancelledAt()).isNull();
        verify(reservations, never()).saveAndFlush(any());
        verify(copies, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Đơn đủ 3 ngày mở cửa nhưng chưa hết giờ đóng cửa ngày thứ 3 -> KHÔNG bị huỷ")
    void threeOpenDaysActive_beforeClosingTime_shouldNotBeCancelled() {
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation reservation = createReservation(1L, "READY_FOR_PICKUP", reservedAt, deadline, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));

        // Kiểm tra lúc 16:30 Thứ Năm (vẫn trong giờ mở cửa ngày thứ 3)
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-08T16:30:00+07:00");
        List<AutoCancelledReservationResponse> result = service.processOverdueReservationsAt(checkTime);

        assertThat(result).isEmpty();
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(reservation.getCancelledAt()).isNull();
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Đơn quá 3 ngày mở cửa -> Tự động huỷ lúc 0h30 ngày kế tiếp, ghi đúng thời điểm huỷ và lý do")
    void overdueAfterThreeOpenDays_atMidnight30_shouldBeAutoCancelled() {
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation reservation = createReservation(1L, "READY_FOR_PICKUP", reservedAt, deadline, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(reservation));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));

        // Kiểm tra lúc 00:30 Thứ Sáu (rạng sáng sau ngày thứ 3 đã kết thúc lúc 17:00 Thứ Năm)
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        List<AutoCancelledReservationResponse> result = service.processOverdueReservationsAt(checkTime);

        assertThat(result).hasSize(1);
        AutoCancelledReservationResponse item = result.get(0);
        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.status()).isEqualTo("CANCELLED");
        assertThat(item.cancellationReason()).isEqualTo("Đã huỷ do quá hạn nhận");
        assertThat(item.cancelledByName()).isEqualTo("Hệ thống");
        assertThat(item.cancelledAt()).isEqualTo(checkTime);

        // Bản sao được giải phóng về Sẵn sàng (AVAILABLE)
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        verify(reservations).saveAndFlush(reservation);
        verify(copies).saveAndFlush(copy);
    }

    @Test
    @DisplayName("Khoảng chờ có ngày đóng cửa xen giữa -> Bỏ qua ngày đóng cửa, chỉ huỷ khi hết đủ 3 ngày mở cửa")
    void intermediateClosedDay_shouldExcludeClosedDay() {
        // Đơn tạo Thứ Hai 10-05. Thứ Tư 10-07 là ngày lễ đóng cửa.
        // 3 ngày mở cửa: Thứ Ba 10-06 (1), Thứ Năm 10-08 (2), Thứ Sáu 10-09 (3, đóng cửa 17:00)
        // Hạn chót nhận: 17:00 Thứ Sáu 10-09
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadlineWithHoliday = OffsetDateTime.parse("2026-10-09T17:00:00+07:00");
        BookCopy copy = createHeldCopy(2L, "BC-002");
        BookReservation reservation = createReservation(2L, "READY_FOR_PICKUP", reservedAt, deadlineWithHoliday, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));
        when(reservations.findForCancellation(2L)).thenReturn(Optional.of(reservation));
        when(copies.findForStatusChange(2L)).thenReturn(Optional.of(copy));

        // 1. Kiểm tra lúc 00:30 Thứ Sáu 10-09 (mới chỉ qua 2 ngày mở cửa là Thứ 3 và Thứ 5) -> KHÔNG huỷ
        OffsetDateTime checkFridayMorning = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        List<AutoCancelledReservationResponse> resultFriday = service.processOverdueReservationsAt(checkFridayMorning);
        assertThat(resultFriday).isEmpty();
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");

        // 2. Kiểm tra lúc 00:30 Thứ Bảy 10-10 (đã kết thúc ngày mở cửa thứ 3 lúc 17:00 Thứ Sáu) -> HUỶ THÀNH CÔNG
        OffsetDateTime checkSaturdayMorning = OffsetDateTime.parse("2026-10-10T00:30:00+07:00");
        List<AutoCancelledReservationResponse> resultSaturday = service.processOverdueReservationsAt(checkSaturdayMorning);
        assertThat(resultSaturday).hasSize(1);
        assertThat(resultSaturday.get(0).status()).isEqualTo("CANCELLED");
        assertThat(resultSaturday.get(0).cancellationReason()).isEqualTo("Đã huỷ do quá hạn nhận");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("Nhiều ngày đóng cửa liên tiếp -> Tính đủ 3 ngày mở cửa trọn vẹn")
    void multipleConsecutiveClosedDays_shouldOnlyCountOpenDays() {
        // Thứ Hai 10-05 tạo đơn. Thứ 4, 5, 6 đóng cửa liên tiếp. Chủ nhật nghỉ hàng tuần.
        // Các ngày mở: Thứ Ba 10-06 (1), Thứ Bảy 10-10 (2), Thứ Hai 10-12 (3, đóng cửa 17:00)
        // Hạn chót nhận: 17:00 Thứ Hai 10-12
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-10-12T17:00:00+07:00");
        BookCopy copy = createHeldCopy(3L, "BC-003");
        BookReservation reservation = createReservation(3L, "READY_FOR_PICKUP", reservedAt, deadline, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));
        when(reservations.findForCancellation(3L)).thenReturn(Optional.of(reservation));
        when(copies.findForStatusChange(3L)).thenReturn(Optional.of(copy));

        // Kiểm tra vào Chủ Nhật 10-11 lúc 00:30 (chưa đủ 3 ngày mở cửa) -> KHÔNG huỷ
        assertThat(service.processOverdueReservationsAt(OffsetDateTime.parse("2026-10-11T00:30:00+07:00"))).isEmpty();

        // Kiểm tra vào Thứ Hai 10-12 lúc 00:30 (ngày mở cửa thứ 3 đang diễn ra) -> KHÔNG huỷ
        assertThat(service.processOverdueReservationsAt(OffsetDateTime.parse("2026-10-12T00:30:00+07:00"))).isEmpty();

        // Kiểm tra vào Thứ Ba 10-13 lúc 00:30 (sau khi kết thúc ngày mở cửa thứ 3) -> HUỶ
        List<AutoCancelledReservationResponse> res = service.processOverdueReservationsAt(OffsetDateTime.parse("2026-10-13T00:30:00+07:00"));
        assertThat(res).hasSize(1);
        assertThat(res.get(0).status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Đơn đã nhận (FULFILLED) và Đơn đã huỷ (CANCELLED) -> KHÔNG bị quét hoặc huỷ lại")
    void fulfilledAndCancelledReservations_shouldBeIgnored() {
        // Repository chỉ trả về các đơn READY_FOR_PICKUP
        when(reservations.findReadyForPickup()).thenReturn(List.of());

        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        List<AutoCancelledReservationResponse> res = service.processOverdueReservationsAt(checkTime);

        assertThat(res).isEmpty();
        verify(reservations, never()).findForCancellation(any());
    }

    @Test
    @DisplayName("Chạy lại trong cùng ngày -> Không huỷ nhầm đơn đã xử lý (Idempotent)")
    void rerunSameDay_isIdempotent() {
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation reservation = createReservation(1L, "READY_FOR_PICKUP", reservedAt, deadline, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(reservation));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));

        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");

        // Lần chạy 1 lúc 00:30
        List<AutoCancelledReservationResponse> firstRun = service.processOverdueReservationsAt(checkTime);
        assertThat(firstRun).hasSize(1);
        assertThat(reservation.getStatus()).isEqualTo("CANCELLED");

        // Lần chạy 2 (giả lập chạy lại lúc 00:35 cùng ngày, findReadyForPickup không còn đơn này)
        when(reservations.findReadyForPickup()).thenReturn(List.of());
        List<AutoCancelledReservationResponse> secondRun = service.processOverdueReservationsAt(checkTime.plusMinutes(5));
        assertThat(secondRun).isEmpty();
    }

    @Test
    @DisplayName("Đơn chưa có pickupDeadline -> Tự động tính hạn nhận từ lịch mở cửa để xác định quá hạn")
    void reservationWithoutPickupDeadline_shouldCalculateFromCalendar() {
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        BookCopy copy = createHeldCopy(4L, "BC-004");
        BookReservation reservation = createReservation(4L, "READY_FOR_PICKUP", reservedAt, null, copy);

        OffsetDateTime computedDeadline = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        when(calendar.calculateReservationPickupDeadline(reservedAt)).thenReturn(computedDeadline);

        when(reservations.findReadyForPickup()).thenReturn(List.of(reservation));
        when(reservations.findForCancellation(4L)).thenReturn(Optional.of(reservation));
        when(copies.findForStatusChange(4L)).thenReturn(Optional.of(copy));

        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        List<AutoCancelledReservationResponse> result = service.processOverdueReservationsAt(checkTime);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("CANCELLED");
        assertThat(result.get(0).cancellationReason()).isEqualTo("Đã huỷ do quá hạn nhận");
    }

    @Test
    @DisplayName("Cấu hình Cron: Đúng 0 giờ 30 phút mỗi ngày theo múi giờ Asia/Ho_Chi_Minh")
    void cronExpression_shouldTriggerAt030DailyInVietnamZone() {
        CronExpression cron = CronExpression.parse("0 30 0 * * *");
        ZonedDateTime start = ZonedDateTime.of(2026, 10, 8, 23, 0, 0, 0, ZONE);
        ZonedDateTime nextExecution = cron.next(start);

        assertThat(nextExecution).isNotNull();
        assertThat(nextExecution.getHour()).isEqualTo(0);
        assertThat(nextExecution.getMinute()).isEqualTo(30);
        assertThat(nextExecution.getSecond()).isEqualTo(0);
        assertThat(nextExecution.getDayOfMonth()).isEqualTo(9);
    }
}
