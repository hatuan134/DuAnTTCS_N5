package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickupCancellationTrialServiceTest {
    private final OffsetDateTime now=OffsetDateTime.parse("2026-10-15T17:01:00+07:00");
    private final BookReservationRepository reservations=mock(BookReservationRepository.class);
    private final BookCopyRepository copies=mock(BookCopyRepository.class);
    private final LibraryConfigurationService config=mock(LibraryConfigurationService.class);
    private final ReservationAutoCancellationService service=new ReservationAutoCancellationService(
            reservations,copies,config,Clock.fixed(now.toInstant(),ZoneOffset.UTC));
    @Test void futureRealRunIsRejectedBeforeAnyDataMutation() {
        assertThrows(ApiException.class,()->service.executeForReservation(20,now.plusDays(1),"manager"));
        assertThrows(ApiException.class,()->service.validateRealCheckTime(now.plusSeconds(1)));
        verifyNoInteractions(reservations,copies,config);
    }
    @Test void receivedOrderAndNotYetExpiredOrderCannotBeCancelled() {
        var r=new BookReservation();r.setId(20L);r.setStatus("FULFILLED");r.setPickupDeadline(now.minusDays(1));
        when(reservations.findForCancellation(20L)).thenReturn(Optional.of(r));
        assertThrows(ApiException.class,()->service.executeForReservation(20,null,"manager"));
        r.setStatus("READY_FOR_PICKUP");r.setPickupDeadline(now.plusSeconds(1));
        assertThrows(ApiException.class,()->service.executeForReservation(20,null,"manager"));
        verify(reservations,never()).saveAndFlush(any());
    }
    @Test void selectedRunCancelsOnlySelectedOrderUsingActualTime() {
        var book=new Book();book.setId(9L);book.setTitle("Mạng máy tính");
        var reader=new User();reader.setId(10L);reader.setFullName("Bạn đọc A");
        var selected=new BookReservation(book,reader,"READY_FOR_PICKUP");selected.setId(20L);selected.setPickupDeadline(now.minusSeconds(1));
        var other=new BookReservation(book,reader,"READY_FOR_PICKUP");other.setId(21L);other.setPickupDeadline(now.minusDays(1));
        when(reservations.findForCancellation(20L)).thenReturn(Optional.of(selected));
        when(reservations.findReadyForPickup()).thenReturn(List.of(selected,other));
        var result=service.executeForReservation(20,now.minusDays(2),"manager");
        assertEquals(1,result.getTotalCancelled());assertEquals("CANCELLED",selected.getStatus());
        assertEquals(now,selected.getCancelledAt());assertEquals("READY_FOR_PICKUP",other.getStatus());
        verify(reservations,never()).findForCancellation(21L);
    }
}
