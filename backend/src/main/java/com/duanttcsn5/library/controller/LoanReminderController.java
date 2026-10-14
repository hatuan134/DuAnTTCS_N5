package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderTriggerRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.LoanReminderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/loan-reminders")
public class LoanReminderController {

    private final LoanReminderService reminderService;

    public LoanReminderController(LoanReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * Preview reminders that would be sent for the given date (default today).
     * Accessible by Librarian, Library Manager, and Admin.
     */
    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanReminderPreviewResponse> previewReminders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LoanReminderPreviewResponse preview = reminderService.getPreview(date);
        return ResponseEntity.ok(preview);
    }

    /**
     * Manually trigger/test-run loan reminders for testing and acceptance.
     * Records the execution with the current staff identity.
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanReminderExecutionResult> triggerReminders(
            @RequestBody(required = false) LoanReminderTriggerRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LocalDate targetDate = request != null ? request.targetDate() : null;
        Long userId = principal != null ? principal.id() : null;
        String userName = principal != null ? principal.fullName() : "Thủ thư";

        LoanReminderExecutionResult result = reminderService.processDailyReminders(
                targetDate,
                "MANUAL_TEST_RUN",
                userId,
                userName
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Query history of sent and failed reminder emails with pagination and filters.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String reminderGroup,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        List<LoanReminderLogResponse> logs = reminderService.getLogs(date, reminderGroup, status, page, size);
        long totalElements = reminderService.countLogs(date, reminderGroup, status);

        return ResponseEntity.ok(Map.of(
                "content", logs,
                "totalElements", totalElements,
                "page", page,
                "size", size,
                "totalPages", (totalElements + size - 1) / Math.max(1, size)
        ));
    }
}
