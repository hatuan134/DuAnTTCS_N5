package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.History;
import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.TriggerRequest;
import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.TriggerResult;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations/{reservationId}/ready-email")
@PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
public class ReadyPickupNotificationController {
    private final ReadyPickupNotificationService service;
    public ReadyPickupNotificationController(ReadyPickupNotificationService service) { this.service=service; }
    @GetMapping public History history(@PathVariable long reservationId) { return service.history(reservationId); }
    @PostMapping("/trigger") public TriggerResult trigger(@PathVariable long reservationId,
            @RequestBody(required=false) TriggerRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        return service.trigger(reservationId,request!=null && Boolean.TRUE.equals(request.simulateFailure()),
                principal==null?null:principal.id());
    }
}
