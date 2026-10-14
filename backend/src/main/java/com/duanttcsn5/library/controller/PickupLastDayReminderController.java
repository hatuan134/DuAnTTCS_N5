package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.PickupLastDayReminderDtos.Report;
import com.duanttcsn5.library.dto.book.PickupLastDayReminderDtos.RunRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reservations/pickup-last-day-reminders")
@PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
public class PickupLastDayReminderController {
    private final ReadyPickupNotificationService service;
    public PickupLastDayReminderController(ReadyPickupNotificationService service) { this.service = service; }
    @GetMapping("/preview")
    public Report preview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.previewLastDay(date);
    }
    @PostMapping("/run")
    public Report run(@Valid @RequestBody RunRequest request,
                      @AuthenticationPrincipal UserPrincipal principal) {
        return service.runLastDayToday(request.date(), principal == null ? null : principal.id());
    }
}
