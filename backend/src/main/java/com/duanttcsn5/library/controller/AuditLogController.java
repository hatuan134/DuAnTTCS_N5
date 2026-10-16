package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.audit.AuditFilterOptionsResponse;
import com.duanttcsn5.library.dto.audit.AuditLogResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(auditLogService.search(fromDate, toDate, actorId, action, keyword));
    }

    @GetMapping("/filter-options")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AuditFilterOptionsResponse> filterOptions() {
        return ResponseEntity.ok(auditLogService.getFilterOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectPut(@PathVariable Long id) {
        throw immutableLogException();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectPatch(@PathVariable Long id) {
        throw immutableLogException();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectDelete(@PathVariable Long id) {
        throw immutableLogException();
    }

    private ApiException immutableLogException() {
        return new ApiException(
                HttpStatus.METHOD_NOT_ALLOWED,
                "AUDIT_LOG_IMMUTABLE",
                "Nhật ký hoạt động chỉ được phép tra cứu, không được sửa hoặc xóa.");
    }
}
