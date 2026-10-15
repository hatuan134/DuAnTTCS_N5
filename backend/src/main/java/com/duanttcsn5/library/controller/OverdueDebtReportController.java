package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.OverdueDebtReportService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/overdue-debts")
@PreAuthorize("hasRole('LIBRARY_MANAGER')")
public class OverdueDebtReportController {
    private final OverdueDebtReportService service;
    public OverdueDebtReportController(OverdueDebtReportService service) { this.service=service; }
    @GetMapping
    public ResponseEntity<Snapshot> preview(@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(actor==null?null:actor.id()));
    }
    // POST: every request intentionally creates an audit entry; no GET prefetch/download cache.
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal actor) {
        var download=service.export(actor==null?null:actor.id());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.filename()).build().toString())
            .header("X-Content-Type-Options","nosniff").body(download.content());
    }
    @GetMapping("/history")
    public ResponseEntity<History> history(@AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(actor==null?null:actor.id(),page,size));
    }
}
