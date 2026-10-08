package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.Role;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerAutoCancelledLookupServiceTest {

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
        OffsetDateTime now = OffsetDateTime.parse("2026-10-09T10:00:00+07:00");
        fixedClock = Clock.fixed(now.toInstant(), ZONE);

        service = new ReservationAutoCancellationService(
                reservations, copies, cards, runRepository, configuration, fixedClock);

        testBook = new Book();
        testBook.setId(10L);
        testBook.setTitle("Lập trình Java căn bản");

        Role role = new Role();
        role.setCode("READER");

        readerA = new User();
        readerA.setId(100L);
        readerA.setFullName("Nguyễn Văn A");
        readerA.setRole(role);
        readerA.setStatus("ACTIVE");

        readerB = new User();
        readerB.setId(200L);
        readerB.setFullName("Trần Thị B");
        readerB.setRole(role);
        readerB.setStatus("ACTIVE");

        copy1 = createHeldCopy(20L, "BC-001", testBook);
    }

    @Test
    @DisplayName("Không có đơn bị huỷ trong 30 ngày: trả về danh sách rỗng")
    void testNoCancelledReservations_returnsEmptyList() {
        when(reservations.findAutoCancelledReservationsSince(any())).thenReturn(List.of());

        List<AutoCancelledReservationResponse> result = service.getAutoCancelledReservationsLast30Days();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Có đơn bị huỷ hôm nay: hiển thị đầy đủ thông tin mã đơn, bạn đọc, sách, bản sao và thời điểm huỷ")
    void testHasCancelledToday_returnsDetailedInformation() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-09T10:00:00+07:00");
        OffsetDateTime cancelledAt = now.minusHours(2);
        OffsetDateTime reservedAt = now.minusDays(4);
        OffsetDateTime deadline = now.minusHours(6);

        BookReservation cancelledRes = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        cancelledRes.setId(101L);
        cancelledRes.setBookCopy(copy1);
        cancelledRes.setReservedAt(reservedAt);
        cancelledRes.setPickupDeadline(deadline);
        cancelledRes.cancelBySystem(cancelledAt, "Đã huỷ do quá hạn nhận");

        when(reservations.findAutoCancelledReservationsSince(any())).thenReturn(List.of(cancelledRes));
        when(reservations.findAllForQueueByBookId(10L)).thenReturn(List.of());

        List<AutoCancelledReservationResponse> result = service.getAutoCancelledReservationsLast30Days();

        assertEquals(1, result.size());
        AutoCancelledReservationResponse item = result.get(0);
        assertEquals(101L, item.id());
        assertEquals(100L, item.readerId());
        assertEquals("Nguyễn Văn A", item.readerName());
        assertEquals(10L, item.bookId());
        assertEquals("Lập trình Java căn bản", item.bookTitle());
        assertEquals("BC-001", item.barcode());
        assertEquals("CANCELLED", item.status());
        assertEquals("Hệ thống", item.cancelledByName());
        assertEquals("Đã huỷ do quá hạn nhận", item.cancellationReason());
        assertEquals(cancelledAt, item.cancelledAt());
        assertEquals("AVAILABLE", item.copyOutcome());
    }

    @Test
    @DisplayName("Nhiều đơn bị huỷ trong 30 ngày: sắp xếp đơn gần nhất lên trước")
    void testMultipleCancelledReservations_orderedNewestFirst() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-09T10:00:00+07:00");

        BookReservation resNewer = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        resNewer.setId(2L);
        resNewer.cancelBySystem(now.minusDays(1), "Đã huỷ do quá hạn nhận");

        BookReservation resOlder = new BookReservation(testBook, readerB, "READY_FOR_PICKUP");
        resOlder.setId(1L);
        resOlder.cancelBySystem(now.minusDays(5), "Đã huỷ do quá hạn nhận");

        when(reservations.findAutoCancelledReservationsSince(any())).thenReturn(List.of(resNewer, resOlder));

        List<AutoCancelledReservationResponse> result = service.getAutoCancelledReservationsLast30Days();

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).id());
        assertEquals(1L, result.get(1).id());
    }

    @Test
    @DisplayName("Kiểm tra mốc 30 ngày: query truyền đúng cutoff là now - 30 ngày")
    void testCutoffPassedToRepository_isExactly30DaysAgo() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-09T10:00:00+07:00");
        OffsetDateTime expectedCutoff = now.minusDays(30);

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        when(reservations.findAutoCancelledReservationsSince(captor.capture())).thenReturn(List.of());

        service.getAutoCancelledReservationsLast30Days();

        assertEquals(expectedCutoff, captor.getValue());
    }

    @Test
    @DisplayName("Bản sao được chuyển cho người tiếp theo: hiển thị thông tin TRANSFERRED và người nhận kế tiếp")
    void testCopyTransferredToNext_showsTransferredOutcomeAndNextReader() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-09T10:00:00+07:00");
        OffsetDateTime cancelledTime = now.minusHours(4);

        BookReservation cancelledRes = new BookReservation(testBook, readerA, "READY_FOR_PICKUP");
        cancelledRes.setId(10L);
        cancelledRes.setBookCopy(copy1);
        cancelledRes.cancelBySystem(cancelledTime, "Đã huỷ do quá hạn nhận");

        // Đơn tiếp theo nhận được bản sao
        BookReservation nextRes = new BookReservation(testBook, readerB, "READY_FOR_PICKUP");
        nextRes.setId(11L);
        nextRes.setBookCopy(copy1);
        nextRes.setReservedAt(cancelledTime);
        nextRes.setPickupDeadline(cancelledTime.plusDays(3));

        when(reservations.findAutoCancelledReservationsSince(any())).thenReturn(List.of(cancelledRes));
        when(reservations.findAllForQueueByBookId(10L)).thenReturn(List.of(cancelledRes, nextRes));

        List<AutoCancelledReservationResponse> result = service.getAutoCancelledReservationsLast30Days();

        assertEquals(1, result.size());
        AutoCancelledReservationResponse item = result.get(0);
        assertEquals("TRANSFERRED", item.copyOutcome());
        assertEquals(11L, item.nextReservationId());
        assertEquals("Trần Thị B", item.nextReaderName());
        assertEquals(cancelledTime.plusDays(3), item.nextPickupDeadline());
    }
}
