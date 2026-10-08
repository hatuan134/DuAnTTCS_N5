package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.AutoCancellationRunResponse;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.ReservationAutoCancellationRun;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.ReservationAutoCancellationRunRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.ReservationAutoCancellationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationAutoCancellationRunServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BookReservationRepository reservations;

    @Mock
    private BookCopyRepository copies;

    @Mock
    private LibraryCardRepository cards;

    @Mock
    private ReservationAutoCancellationRunRepository runRepository;

    @Mock
    private LibraryConfigurationService configuration;

    private Clock fixedClock;
    private ReservationAutoCancellationService service;

    private Book testBook;
    private User readerA;
    private User readerB;
    private BookCopy copy1;
    private LibraryCard cardB;

    private BookCopy createHeldCopy(long id, String barcode, Book book) {
        BookCopy copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "barcode", barcode);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "status", "HELD");
        return copy;
    }

    @BeforeEach
    void setUp() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        fixedClock = Clock.fixed(checkTime.toInstant(), ZONE);

        service = new ReservationAutoCancellationService(
                reservations, copies, cards, runRepository, configuration, fixedClock);

        testBook = new Book();
        testBook.setId(10L);
        testBook.setTitle("Lập trình Java căn bản");

        readerA = new User();
        readerA.setId(100L);
        readerA.setFullName("Nguyễn Văn A");
        readerA.setStatus("ACTIVE");

        readerB = new User();
        readerB.setId(200L);
        readerB.setFullName("Trần Thị B");
        readerB.setStatus("ACTIVE");

        copy1 = createHeldCopy(20L, "BC-001", testBook);

        cardB = new LibraryCard();
        cardB.setUser(readerB);
        cardB.setStatus("ACTIVE");
        cardB.setExpiresAt(LocalDate.of(2027, 1, 1));

        // Mặc định saveAndFlush trả về chính đối tượng được truyền vào và gán ID nếu chưa có
        lenient().when(runRepository.saveAndFlush(any(ReservationAutoCancellationRun.class)))
                .thenAnswer(inv -> {
                    ReservationAutoCancellationRun r = inv.getArgument(0);
                    if (r.getId() == null) {
                        r.setId(999L);
                    }
                    return r;
                });
    }

    @Test
    @DisplayName("Chạy lần đầu có nhiều đơn quá hạn: ghi nhận đầy đủ run record và thống kê")
    void testFirstRun_withMultipleOverdueReservations_recordsStatisticsAccurately() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");

        // Đơn 1 quá hạn, có người xếp hàng (readerB) -> bản sao được chuyển
        BookReservation res1 = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        res1.setId(1L);
        res1.setBookCopy(copy1);
        res1.setPickupDeadline(checkTime.minusHours(5));

        // Đơn 2 quá hạn, không có ai xếp hàng -> bản sao trả về AVAILABLE
        Book testBook2 = new Book();
        testBook2.setId(11L);
        BookCopy copy2 = createHeldCopy(21L, "BC-002", testBook2);

        BookReservation res2 = new BookReservation(testBook2, readerA, "READY_FOR_PICKUP");
        res2.setId(2L);
        res2.setBookCopy(copy2);
        res2.setPickupDeadline(checkTime.minusHours(2));

        BookReservation waiterB = new BookReservation(testBook, readerB, "PENDING");
        waiterB.setId(3L);

        when(reservations.findReadyForPickup()).thenReturn(List.of(res1, res2));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(res1));
        when(reservations.findForCancellation(2L)).thenReturn(Optional.of(res2));
        when(copies.findForStatusChange(20L)).thenReturn(Optional.of(copy1));
        when(copies.findForStatusChange(21L)).thenReturn(Optional.of(copy2));
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of(waiterB));
        when(reservations.findPendingQueueForAllocation(11L)).thenReturn(List.of());
        when(cards.findByUserIdWithDetails(200L)).thenReturn(Optional.of(cardB));
        when(configuration.calculateReservationPickupDeadline(checkTime))
                .thenReturn(checkTime.plusDays(3));

        AutoCancellationRunResponse result = service.executeAutoCancellationRun(checkTime, "SCHEDULED_JOB");

        assertNotNull(result);
        assertEquals(LocalDate.of(2026, 10, 9), result.getRunDate());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(2, result.getTotalIdentified());
        assertEquals(2, result.getTotalCancelled());
        assertEquals(1, result.getTotalTransferred());
        assertEquals(1, result.getTotalReleased());
        assertEquals(0, result.getErrorCount());
        assertNull(result.getErrorMessage());
        assertEquals("SCHEDULED_JOB", result.getTriggeredBy());

        // Kiểm tra đơn đã được liên kết với runRecord
        assertNotNull(res1.getAutoCancellationRun());
        assertNotNull(res2.getAutoCancellationRun());
        assertEquals("CANCELLED", res1.getStatus());
        assertEquals("CANCELLED", res2.getStatus());
        assertEquals("AVAILABLE", copy2.getStatus());
        assertEquals("READY_FOR_PICKUP", waiterB.getStatus());
    }

    @Test
    @DisplayName("Chạy lại ngay trong cùng ngày: không huỷ lại đơn cũ, không chuyển nhầm bản sao, không nhân đôi dữ liệu")
    void testRerunInSameDay_idempotent_noDoubleProcessing() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");

        // Giả lập sau khi đã chạy lần 1: Đơn 1 đã CANCELLED và có run record
        ReservationAutoCancellationRun prevRun = new ReservationAutoCancellationRun();
        prevRun.setId(100L);

        BookReservation res1 = new BookReservation(testBook, readerA, "CANCELLED");
        res1.setId(1L);
        res1.setBookCopy(copy1);
        res1.setAutoCancellationRun(prevRun);
        res1.setPickupDeadline(checkTime.minusHours(5));

        // Đơn của người tiếp theo (waiterB) nay là READY_FOR_PICKUP nhưng hạn nhận mới còn 3 ngày -> KHÔNG quá hạn
        BookReservation waiterB = new BookReservation(testBook, readerB, "READY_FOR_PICKUP");
        waiterB.setId(3L);
        waiterB.setBookCopy(copy1);
        waiterB.setReservedAt(checkTime);
        waiterB.setPickupDeadline(checkTime.plusDays(3));

        // findReadyForPickup chỉ trả về waiterB (vì res1 đã CANCELLED)
        when(reservations.findReadyForPickup()).thenReturn(List.of(waiterB));

        // Chạy lại lần 2
        AutoCancellationRunResponse rerunResult = service.executeAutoCancellationRun(checkTime.plusMinutes(10), "SYSTEM");

        assertNotNull(rerunResult);
        assertEquals("SUCCESS", rerunResult.getStatus());
        assertEquals(0, rerunResult.getTotalIdentified(), "Không có đơn nào bị nhận diện quá hạn");
        assertEquals(0, rerunResult.getTotalCancelled(), "Không có đơn nào bị huỷ lặp lại");
        assertEquals(0, rerunResult.getTotalTransferred(), "Không có bản sao nào bị chuyển lại");
        assertEquals(0, rerunResult.getTotalReleased(), "Không có bản sao nào bị trả về sai");
        assertEquals(0, rerunResult.getErrorCount());
        assertTrue(rerunResult.getCancelledReservations().isEmpty());

        // Đơn waiterB và bản sao copy1 vẫn nguyên vẹn
        assertEquals("READY_FOR_PICKUP", waiterB.getStatus());
        assertEquals("HELD", copy1.getStatus());
        verify(reservations, never()).findForCancellation(any());
    }

    @Test
    @DisplayName("Chạy nhiều lần liên tiếp: kết quả luôn an toàn và nhất quán")
    void testMultipleConsecutiveRuns_remainsConsistent() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        when(reservations.findReadyForPickup()).thenReturn(List.of());

        for (int i = 0; i < 3; i++) {
            AutoCancellationRunResponse res = service.executeAutoCancellationRun(checkTime.plusMinutes(i * 5), "TEST_RUNNER");
            assertEquals("SUCCESS", res.getStatus());
            assertEquals(0, res.getTotalIdentified());
            assertEquals(0, res.getTotalCancelled());
            assertEquals(0, res.getTotalTransferred());
            assertEquals(0, res.getTotalReleased());
        }

        // Mỗi lần chạy lưu 2 lần: lúc bắt đầu để lấy ID gán vào đơn và lúc kết thúc để cập nhật thống kê
        verify(runRepository, times(6)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Không có đơn quá hạn: tạo bản ghi run với số liệu 0 đơn")
    void testNoOverdueReservations_createsZeroCountRun() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");

        // Đơn chưa hết hạn (hạn còn 2 ngày nữa)
        BookReservation activeRes = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        activeRes.setId(10L);
        activeRes.setPickupDeadline(checkTime.plusDays(2));

        when(reservations.findReadyForPickup()).thenReturn(List.of(activeRes));

        AutoCancellationRunResponse result = service.executeAutoCancellationRun(checkTime, "SYSTEM");

        assertEquals(0, result.getTotalIdentified());
        assertEquals(0, result.getTotalCancelled());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("READY_FOR_PICKUP", activeRes.getStatus());
    }

    @Test
    @DisplayName("Một đơn đã được xử lý trước đó có autoCancellationRun: bị bỏ qua ngay lập tức")
    void testReservationAlreadyProcessed_skippedImmediately() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");

        ReservationAutoCancellationRun previousRun = new ReservationAutoCancellationRun();
        previousRun.setId(50L);

        BookReservation alreadyProcessed = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        alreadyProcessed.setId(1L);
        alreadyProcessed.setAutoCancellationRun(previousRun);
        alreadyProcessed.setPickupDeadline(checkTime.minusDays(1));

        when(reservations.findReadyForPickup()).thenReturn(List.of(alreadyProcessed));

        AutoCancellationRunResponse result = service.executeAutoCancellationRun(checkTime, "SYSTEM");

        assertEquals(0, result.getTotalIdentified());
        assertEquals(0, result.getTotalCancelled());
        verify(reservations, never()).findForCancellation(1L);
    }

    @Test
    @DisplayName("Chốt với PO: Ghi nhận thất bại một phần (PARTIAL_FAILURE) khi 1 đơn gặp lỗi nhưng đơn khác thành công")
    void testPartialFailure_whenOneReservationFails_otherSucceedsAndRunMarkedPartialFailure() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");

        // Đơn 1 bị lỗi cơ sở dữ liệu khi save
        BookReservation failRes = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        failRes.setId(1L);
        failRes.setBookCopy(copy1);
        failRes.setPickupDeadline(checkTime.minusHours(5));

        // Đơn 2 thành công
        Book testBook2 = new Book();
        testBook2.setId(12L);
        BookCopy copy2 = createHeldCopy(22L, "BC-002", testBook2);
        BookReservation successRes = new BookReservation(testBook2, readerB, "READY_FOR_PICKUP");
        successRes.setId(2L);
        successRes.setBookCopy(copy2);
        successRes.setPickupDeadline(checkTime.minusHours(3));

        when(reservations.findReadyForPickup()).thenReturn(List.of(failRes, successRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(failRes));
        when(reservations.findForCancellation(2L)).thenReturn(Optional.of(successRes));
        when(copies.findForStatusChange(22L)).thenReturn(Optional.of(copy2));
        when(reservations.findPendingQueueForAllocation(12L)).thenReturn(List.of());

        // Mô phỏng failRes ném exception khi flush
        doThrow(new RuntimeException("Lỗi deadlock kết nối DB")).when(reservations).saveAndFlush(failRes);

        AutoCancellationRunResponse result = service.executeAutoCancellationRun(checkTime, "SYSTEM");

        assertNotNull(result);
        assertEquals("PARTIAL_FAILURE", result.getStatus(), "Phải ghi nhận PARTIAL_FAILURE khi có đơn lỗi và đơn thành công");
        assertEquals(2, result.getTotalIdentified());
        assertEquals(1, result.getTotalCancelled());
        assertEquals(1, result.getErrorCount());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Đơn #1: Lỗi deadlock kết nối DB"));
        assertEquals(1, result.getTotalReleased());
    }

    @Test
    @DisplayName("Hiển thị kết quả lần chạy gần nhất cho Quản lý kiểm tra")
    void testGetLatestRun_returnsMostRecentRunResponse() {
        ReservationAutoCancellationRun latestRun = new ReservationAutoCancellationRun(
                LocalDate.of(2026, 10, 9),
                OffsetDateTime.parse("2026-10-09T00:30:00+07:00"),
                OffsetDateTime.parse("2026-10-09T00:30:01+07:00"),
                "SUCCESS",
                5, 5, 3, 2, 0, null, "SYSTEM"
        );
        latestRun.setId(88L);

        when(runRepository.findFirstByOrderByStartedAtDesc()).thenReturn(Optional.of(latestRun));

        Optional<AutoCancellationRunResponse> optResponse = service.getLatestRun();

        assertTrue(optResponse.isPresent());
        AutoCancellationRunResponse response = optResponse.get();
        assertEquals(88L, response.getId());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(5, response.getTotalIdentified());
        assertEquals(5, response.getTotalCancelled());
        assertEquals(3, response.getTotalTransferred());
        assertEquals(2, response.getTotalReleased());
    }
}
