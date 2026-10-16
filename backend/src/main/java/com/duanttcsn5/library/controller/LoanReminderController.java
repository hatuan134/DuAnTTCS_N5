package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderRetryExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderRetryRequest;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderTriggerRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.LoanReminderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * Supports simulateFailure flag for testing retry mechanism.
     * Records the execution with the current staff identity.
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanReminderExecutionResult> triggerReminders(
            @RequestBody(required = false) LoanReminderTriggerRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LocalDate targetDate = request != null ? request.targetDate() : null;
        boolean simulateFailure = request != null && Boolean.TRUE.equals(request.simulateFailure());
        Long userId = principal != null ? principal.id() : null;
        String userName = principal != null ? principal.fullName() : "Thủ thư";

        LoanReminderExecutionResult result = reminderService.processDailyReminders(
                targetDate,
                "MANUAL_TEST_RUN",
                userId,
                userName,
                simulateFailure
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Retry pending failed emails (or all pending if no specific logId provided).
     * Supports simulateFailure flag for testing subsequent retry attempts.
     */
    @PostMapping("/retry-pending")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanReminderRetryExecutionResult> retryPending(
            @RequestBody(required = false) LoanReminderRetryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long specificLogId = request != null ? request.logId() : null;
        boolean simulateFailure = request != null && Boolean.TRUE.equals(request.simulateFailure());
        Long userId = principal != null ? principal.id() : null;
        String userName = principal != null ? principal.fullName() : "Thủ thư";

        LoanReminderRetryExecutionResult result = reminderService.processPendingRetries(
                specificLogId,
                simulateFailure,
                userId,
                userName
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Retry a specific failed email log by ID.
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanReminderRetryExecutionResult> retrySingle(
            @PathVariable Long id,
            @RequestBody(required = false) LoanReminderRetryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean simulateFailure = request != null && Boolean.TRUE.equals(request.simulateFailure());
        Long userId = principal != null ? principal.id() : null;
        String userName = principal != null ? principal.fullName() : "Thủ thư";

        LoanReminderRetryExecutionResult result = reminderService.processPendingRetries(
                id,
                simulateFailure,
                userId,
                userName
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Query history of sent and failed reminder emails with pagination and filters.
     * Filters: date, reminderGroup, status, search (reader name/email), cardNumber.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String reminderGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cardNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        List<LoanReminderLogResponse> logs = reminderService.getLogs(date, reminderGroup, status, search, cardNumber, page, size);
        long totalElements = reminderService.countLogs(date, reminderGroup, status, search, cardNumber);

        return ResponseEntity.ok(Map.of(
                "content", logs,
                "totalElements", totalElements,
                "page", page,
                "size", size,
                "totalPages", (totalElements + size - 1) / Math.max(1, size)
        ));
    }
}
