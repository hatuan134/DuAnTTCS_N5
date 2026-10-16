package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.PermissionReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/permission-review")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionReviewController {
    private final PermissionReviewService service;
    @org.springframework.beans.factory.annotation.Autowired private com.duanttcsn5.library.service.PermissionReviewRunService runs;
    @org.springframework.beans.factory.annotation.Autowired private com.duanttcsn5.library.service.PermissionReviewCoverageService coverage;
    public PermissionReviewController(PermissionReviewService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Inspection> inspect() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.inspect());
    }
    @GetMapping("/reports")
    public ResponseEntity<ReportPage> reports(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.reports(page, size));
    }
    @GetMapping("/reports/{id}")
    public ResponseEntity<ReportDetail> detail(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.detail(id));
    }
    @PostMapping("/reports")
    public ResponseEntity<ReportDetail> importReport(@Valid @RequestBody ImportReport report,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importReport(report, principal.id()));
    }

    @PostMapping("/runs")
    public ResponseEntity<RunState> startRun(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runs.start(principal.id()));
    }
    @GetMapping("/runs/latest")
    public ResponseEntity<RunState> latestRun() {return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(runs.latest());}
    @GetMapping("/runs/{runKey}")
    public ResponseEntity<RunState> runStatus(@PathVariable java.util.UUID runKey) {return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(runs.get(runKey));}
    @GetMapping("/reports/{id}/coverage")
    public ResponseEntity<Coverage> coverage(@PathVariable Long id) {return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(coverage.coverage(id));}
    @PostMapping("/reports/{id}/complete")
    public ResponseEntity<Coverage> complete(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal principal) {return ResponseEntity.ok(coverage.complete(id,principal.id()));}
    @GetMapping("/reports/{id}/export")
    public ResponseEntity<ExportReport> export(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Content-Disposition","attachment; filename=BienBan_S4-09.2_"+id+".json").body(coverage.export(id));
    }
}
