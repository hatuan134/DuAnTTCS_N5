package com.duanttcsn5.library;

import com.duanttcsn5.library.service.LibraryConfigurationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Keeps earlier eligibility test fixtures focused on guards, not the calendar.
 * Real calendar/date arithmetic is tested separately with mocked weekly/closed-date stores.
 */
final class RenewalCalendarStub {
    private RenewalCalendarStub() {}

    static LibraryConfigurationService mockCalendar() {
        LibraryConfigurationService calendar = mock(LibraryConfigurationService.class);
        when(calendar.calculateRenewalDueAt(any(OffsetDateTime.class), anyInt())).thenAnswer(inv -> {
            OffsetDateTime current = inv.getArgument(0);
            int days = inv.getArgument(1);
            var vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
            return current.atZoneSameInstant(vietnam).toLocalDate().plusDays(days)
                    .atTime(17, 0).atZone(vietnam).toOffsetDateTime();
        });
        return calendar;
    }
}
