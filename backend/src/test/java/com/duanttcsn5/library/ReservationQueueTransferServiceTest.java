package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.ReservationAutoCancellationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationQueueTransferServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BookReservationRepository reservations;
    private BookCopyRepository copies;
    private LibraryCardRepository cards;
    private LibraryConfigurationService calendar;
    private ReservationAutoCancellationService service;

    private Book book;
    private User readerA;
    private User readerB;
    private User readerC;

    @BeforeEach
    void setUp() {
        reservations = mock(BookReservationRepository.class);
        copies = mock(BookCopyRepository.class);
        cards = mock(LibraryCardRepository.class);
        calendar = mock(LibraryConfigurationService.class);
        service = new ReservationAutoCancellationService(reservations, copies, cards, calendar, Clock.system(ZONE));

        book = new Book();
        book.setId(10L);
        book.setTitle("Lập trình Java căn bản");

        Role role = new Role();
        role.setCode("READER");

        readerA = createUser(100L, "Nguyễn Văn A", role, "ACTIVE");
        readerB = createUser(200L, "Trần Thị B", role, "ACTIVE");
        readerC = createUser(300L, "Lê Văn C", role, "ACTIVE");
    }

    private User createUser(long id, String name, Role role, String status) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private LibraryCard createCard(long id, User user, String cardNumber, String status, LocalDate expiresAt) {
        LibraryCard c = new LibraryCard();
        c.setId(id);
        c.setUser(user);
        c.setCardNumber(cardNumber);
        c.setStatus(status);
        c.setExpiresAt(expiresAt);
        return c;
    }

    private BookCopy createHeldCopy(long id, String barcode) {
        BookCopy copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "barcode", barcode);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "status", "HELD");
        return copy;
    }

    private BookReservation createReservation(long id, User reader, String status, OffsetDateTime reservedAt, OffsetDateTime deadline, BookCopy copy) {
        BookReservation r = new BookReservation(book, reader, status);
        r.setId(id);
        r.setReservedAt(reservedAt);
        r.setPickupDeadline(deadline);
        r.setBookCopy(copy);
        return r;
    }

    @Test
    @DisplayName("Hàng đợi rỗng -> Khi đơn bị huỷ quá hạn, bản sao được trả về trạng thái Sẵn sàng (AVAILABLE)")
    void emptyQueue_whenOverdueCancelled_shouldReleaseCopyToAvailable() {
        OffsetDateTime reservedAt = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation overdueRes = createReservation(1L, readerA, "READY_FOR_PICKUP", reservedAt, deadline, copy);

        when(reservations.findReadyForPickup()).thenReturn(List.of(overdueRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(overdueRes));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));

        // Hàng đợi rỗng
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of());

        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        List<AutoCancelledReservationResponse> results = service.processOverdueReservationsAt(checkTime);

        assertThat(results).hasSize(1);
        AutoCancelledReservationResponse result = results.get(0);
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.copyOutcome()).isEqualTo("AVAILABLE");
        assertThat(result.nextReservationId()).isNull();

        // Bản sao được giải phóng về Sẵn sàng
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        verify(copies).saveAndFlush(copy);
    }

    @Test
    @DisplayName("Có một người chờ hợp lệ -> Chuyển bản sao cho người tiếp theo, cập nhật Chờ nhận và tính hạn mới")
    void singleWaitingReader_valid_shouldTransferCopyToNextWaiter() {
        OffsetDateTime reservedAtA = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
        OffsetDateTime deadlineA = OffsetDateTime.parse("2026-10-08T17:00:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation overdueRes = createReservation(1L, readerA, "READY_FOR_PICKUP", reservedAtA, deadlineA, copy);

        // Người B trong hàng đợi, có thẻ hợp lệ
        OffsetDateTime queueTimeB = OffsetDateTime.parse("2026-10-06T10:00:00+07:00");
        BookReservation pendingResB = createReservation(2L, readerB, "PENDING", queueTimeB, null, null);

        LibraryCard cardB = createCard(20L, readerB, "CARD-200", "ACTIVE", LocalDate.parse("2027-01-01"));
        when(cards.findByUserIdWithDetails(200L)).thenReturn(Optional.of(cardB));

        when(reservations.findReadyForPickup()).thenReturn(List.of(overdueRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(overdueRes));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of(pendingResB));

        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        OffsetDateTime expectedDeadlineB = OffsetDateTime.parse("2026-10-12T17:00:00+07:00");
        when(calendar.calculateReservationPickupDeadline(checkTime)).thenReturn(expectedDeadlineB);

        List<AutoCancelledReservationResponse> results = service.processOverdueReservationsAt(checkTime);

        assertThat(results).hasSize(1);
        AutoCancelledReservationResponse result = results.get(0);
        assertThat(result.copyOutcome()).isEqualTo("TRANSFERRED");
        assertThat(result.nextReservationId()).isEqualTo(2L);
        assertThat(result.nextReaderName()).isEqualTo("Trần Thị B");
        assertThat(result.nextPickupDeadline()).isEqualTo(expectedDeadlineB);

        // Đơn của người B chuyển sang READY_FOR_PICKUP, giữ bản sao BC-001, hạn nhận mới
        assertThat(pendingResB.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(pendingResB.getBookCopy()).isSameAs(copy);
        assertThat(pendingResB.getReservedAt()).isEqualTo(checkTime);
        assertThat(pendingResB.getPickupDeadline()).isEqualTo(expectedDeadlineB);
        verify(reservations).saveAndFlush(pendingResB);

        // Bản sao vẫn giữ HELD vì đã chuyển chủ sở hữu
        assertThat(copy.getStatus()).isEqualTo("HELD");
        verify(copies, never()).saveAndFlush(copy);
    }

    @Test
    @DisplayName("Có nhiều người chờ, người đầu hợp lệ -> Chuyển cho người đầu, người sau giữ nguyên PENDING")
    void multipleWaitingReaders_firstValid_shouldTransferToFirstAndRetainOthers() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation overdueRes = createReservation(1L, readerA, "READY_FOR_PICKUP",
                checkTime.minusDays(4), checkTime.minusHours(7), copy);

        BookReservation pendingB = createReservation(2L, readerB, "PENDING", checkTime.minusDays(2), null, null);
        BookReservation pendingC = createReservation(3L, readerC, "PENDING", checkTime.minusDays(1), null, null);

        LibraryCard cardB = createCard(20L, readerB, "CARD-200", "ACTIVE", LocalDate.parse("2027-01-01"));
        when(cards.findByUserIdWithDetails(200L)).thenReturn(Optional.of(cardB));

        when(reservations.findReadyForPickup()).thenReturn(List.of(overdueRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(overdueRes));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of(pendingB, pendingC));

        OffsetDateTime newDeadline = OffsetDateTime.parse("2026-10-12T17:00:00+07:00");
        when(calendar.calculateReservationPickupDeadline(checkTime)).thenReturn(newDeadline);

        service.processOverdueReservationsAt(checkTime);

        // Người B được nhận bản sao
        assertThat(pendingB.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(pendingB.getBookCopy()).isSameAs(copy);

        // Người C giữ nguyên PENDING, không có bản sao
        assertThat(pendingC.getStatus()).isEqualTo("PENDING");
        assertThat(pendingC.getBookCopy()).isNull();
        verify(reservations, never()).saveAndFlush(pendingC);
    }

    @Test
    @DisplayName("Người đầu không hợp lệ (thẻ hết hạn / bị khóa) -> Bỏ qua người đầu, chuyển cho người thứ 2 hợp lệ")
    void firstWaitingReaderIneligible_secondValid_shouldSkipFirstAndTransferToSecond() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation overdueRes = createReservation(1L, readerA, "READY_FOR_PICKUP",
                checkTime.minusDays(4), checkTime.minusHours(7), copy);

        BookReservation pendingB = createReservation(2L, readerB, "PENDING", checkTime.minusDays(2), null, null);
        BookReservation pendingC = createReservation(3L, readerC, "PENDING", checkTime.minusDays(1), null, null);

        // Người B có thẻ bị khóa (LOCKED)
        LibraryCard cardB = createCard(20L, readerB, "CARD-200", "LOCKED", LocalDate.parse("2027-01-01"));
        when(cards.findByUserIdWithDetails(200L)).thenReturn(Optional.of(cardB));

        // Người C có thẻ hợp lệ (ACTIVE)
        LibraryCard cardC = createCard(30L, readerC, "CARD-300", "ACTIVE", LocalDate.parse("2027-01-01"));
        when(cards.findByUserIdWithDetails(300L)).thenReturn(Optional.of(cardC));

        when(reservations.findReadyForPickup()).thenReturn(List.of(overdueRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(overdueRes));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of(pendingB, pendingC));

        OffsetDateTime newDeadline = OffsetDateTime.parse("2026-10-12T17:00:00+07:00");
        when(calendar.calculateReservationPickupDeadline(checkTime)).thenReturn(newDeadline);

        List<AutoCancelledReservationResponse> results = service.processOverdueReservationsAt(checkTime);

        // Kết quả ghi nhận chuyển cho người C
        assertThat(results.get(0).copyOutcome()).isEqualTo("TRANSFERRED");
        assertThat(results.get(0).nextReservationId()).isEqualTo(3L);
        assertThat(results.get(0).nextReaderName()).isEqualTo("Lê Văn C");

        // Người B bị bỏ qua nhưng vẫn giữ nguyên trong hàng đợi (PENDING)
        assertThat(pendingB.getStatus()).isEqualTo("PENDING");
        assertThat(pendingB.getBookCopy()).isNull();

        // Người C nhận được bản sao
        assertThat(pendingC.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(pendingC.getBookCopy()).isSameAs(copy);
        assertThat(pendingC.getPickupDeadline()).isEqualTo(newDeadline);
        verify(reservations).saveAndFlush(pendingC);
    }

    @Test
    @DisplayName("Tất cả người trong hàng đợi đều không hợp lệ -> Bỏ qua tất cả, trả bản sao về Sẵn sàng")
    void allWaitingReadersIneligible_shouldSkipAllAndReleaseToAvailable() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation overdueRes = createReservation(1L, readerA, "READY_FOR_PICKUP",
                checkTime.minusDays(4), checkTime.minusHours(7), copy);

        BookReservation pendingB = createReservation(2L, readerB, "PENDING", checkTime.minusDays(2), null, null);
        BookReservation pendingC = createReservation(3L, readerC, "PENDING", checkTime.minusDays(1), null, null);

        // Người B có thẻ hết hạn (EXPIRED)
        LibraryCard cardB = createCard(20L, readerB, "CARD-200", "EXPIRED", LocalDate.parse("2025-01-01"));
        when(cards.findByUserIdWithDetails(200L)).thenReturn(Optional.of(cardB));

        // Người C có thẻ bị khóa (LOCKED)
        LibraryCard cardC = createCard(30L, readerC, "CARD-300", "LOCKED", LocalDate.parse("2027-01-01"));
        when(cards.findByUserIdWithDetails(300L)).thenReturn(Optional.of(cardC));

        when(reservations.findReadyForPickup()).thenReturn(List.of(overdueRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(overdueRes));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of(pendingB, pendingC));

        List<AutoCancelledReservationResponse> results = service.processOverdueReservationsAt(checkTime);

        // Kết quả: Bản sao trả về Sẵn sàng vì không có ai hợp lệ
        assertThat(results.get(0).copyOutcome()).isEqualTo("AVAILABLE");
        assertThat(results.get(0).nextReservationId()).isNull();

        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        verify(copies).saveAndFlush(copy);

        // Cả 2 người vẫn giữ nguyên trạng thái PENDING
        assertThat(pendingB.getStatus()).isEqualTo("PENDING");
        assertThat(pendingC.getStatus()).isEqualTo("PENDING");
        verify(reservations, never()).saveAndFlush(pendingB);
        verify(reservations, never()).saveAndFlush(pendingC);
    }

    @Test
    @DisplayName("Đảm bảo bản sao chỉ thuộc về DUY NHẤT một đơn mới khi chuyển hàng đợi")
    void copyOnlyAssignedToSingleNewReservation() {
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        BookCopy copy = createHeldCopy(1L, "BC-001");
        BookReservation overdueRes = createReservation(1L, readerA, "READY_FOR_PICKUP",
                checkTime.minusDays(4), checkTime.minusHours(7), copy);

        BookReservation pendingB = createReservation(2L, readerB, "PENDING", checkTime.minusDays(2), null, null);
        BookReservation pendingC = createReservation(3L, readerC, "PENDING", checkTime.minusDays(1), null, null);

        LibraryCard cardB = createCard(20L, readerB, "CARD-200", "ACTIVE", LocalDate.parse("2027-01-01"));
        when(cards.findByUserIdWithDetails(200L)).thenReturn(Optional.of(cardB));

        when(reservations.findReadyForPickup()).thenReturn(List.of(overdueRes));
        when(reservations.findForCancellation(1L)).thenReturn(Optional.of(overdueRes));
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));
        when(reservations.findPendingQueueForAllocation(10L)).thenReturn(List.of(pendingB, pendingC));
        when(calendar.calculateReservationPickupDeadline(checkTime)).thenReturn(checkTime.plusDays(3));

        service.processOverdueReservationsAt(checkTime);

        // Duy nhất pendingB được gán copy
        assertThat(pendingB.getBookCopy()).isSameAs(copy);
        assertThat(pendingC.getBookCopy()).isNull();
    }
}
