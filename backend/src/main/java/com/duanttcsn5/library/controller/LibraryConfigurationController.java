package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.libraryconfig.BulkClosedDatesRequest;
import com.duanttcsn5.library.dto.libraryconfig.ClosedDateRequest;
import com.duanttcsn5.library.dto.libraryconfig.ClosedDateResponse;
import com.duanttcsn5.library.dto.libraryconfig.DueDateAdjustmentResponse;
import com.duanttcsn5.library.dto.libraryconfig.ShelfRequest;
import com.duanttcsn5.library.dto.libraryconfig.ShelfResponse;
import com.duanttcsn5.library.dto.libraryconfig.WarehouseRequest;
import com.duanttcsn5.library.dto.libraryconfig.WarehouseResponse;
import com.duanttcsn5.library.dto.libraryconfig.WeeklyScheduleResponse;
import com.duanttcsn5.library.dto.libraryconfig.WeeklyScheduleUpdateRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/library-settings")
public class LibraryConfigurationController {

    private final LibraryConfigurationService service;

    public LibraryConfigurationController(LibraryConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<WarehouseResponse>> getWarehouses() {
        return ResponseEntity.ok(service.getWarehouses());
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @Valid @RequestBody WarehouseRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createWarehouse(request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.updateWarehouse(id, request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @GetMapping("/shelves")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<ShelfResponse>> getShelves(
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(service.getShelves(warehouseId));
    }

    @PostMapping("/shelves")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ShelfResponse> createShelf(
            @Valid @RequestBody ShelfRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createShelf(request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @PutMapping("/shelves/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ShelfResponse> updateShelf(
            @PathVariable Long id,
            @Valid @RequestBody ShelfRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.updateShelf(id, request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @DeleteMapping("/shelves/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteShelf(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        service.deleteShelf(id, principal.id(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/weekly-schedule")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<WeeklyScheduleResponse>> getWeeklySchedule() {
        return ResponseEntity.ok(service.getWeeklySchedule());
    }

    @PutMapping("/weekly-schedule")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<WeeklyScheduleResponse>> updateWeeklySchedule(
            @Valid @RequestBody WeeklyScheduleUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.updateWeeklySchedule(request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @GetMapping("/closed-dates")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<ClosedDateResponse>> getClosedDates(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(service.getClosedDates(year));
    }

    @PostMapping("/closed-dates")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ClosedDateResponse> createClosedDate(
            @Valid @RequestBody ClosedDateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createClosedDate(request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/closed-dates/bulk")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<ClosedDateResponse>> createClosedDatesBulk(
            @Valid @RequestBody BulkClosedDatesRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createClosedDatesBulk(request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @PutMapping("/closed-dates/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ClosedDateResponse> updateClosedDate(
            @PathVariable Long id,
            @Valid @RequestBody ClosedDateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.updateClosedDate(id, request, principal.id(), servletRequest.getRemoteAddr()));
    }

    @DeleteMapping("/closed-dates/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteClosedDate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        service.deleteClosedDate(id, principal.id(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/adjust-due-date")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<DueDateAdjustmentResponse> adjustDueDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.adjustDueDate(date));
    }
}
