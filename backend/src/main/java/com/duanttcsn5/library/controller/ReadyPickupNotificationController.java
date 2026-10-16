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
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.duanttcsn5.library.service.ReservationAutoCancellationService cancellations;
    public ReadyPickupNotificationController(ReadyPickupNotificationService service) { this.service=service; }
    @GetMapping public History history(@PathVariable long reservationId) { return service.history(reservationId); }
    @GetMapping("/cancellation-preview")
    public com.duanttcsn5.library.dto.book.PickupCancellationDtos.Preview previewCancellation(
            @PathVariable long reservationId,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(
                    iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.OffsetDateTime checkTime) {
        return service.previewCancellation(reservationId,checkTime);
    }
    @PostMapping("/cancellation-run")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER','ADMIN')")
    public com.duanttcsn5.library.dto.book.PickupCancellationDtos.RunResult runCancellation(
            @PathVariable long reservationId,
            @RequestBody(required=false) com.duanttcsn5.library.dto.book.PickupCancellationDtos.RunRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (cancellations==null) throw new com.duanttcsn5.library.exception.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"CANCELLATION_SERVICE_UNAVAILABLE",
                "Chưa sẵn sàng chạy thử hủy đơn. Vui lòng thử lại.");
        var run=cancellations.executeForReservation(reservationId,request==null?null:request.checkTime(),
                principal==null?"MANUAL_TRIGGER":principal.email());
        return new com.duanttcsn5.library.dto.book.PickupCancellationDtos.RunResult(
                run.getTotalCancelled()>0?"Đã hủy đơn quá hạn. Email được xử lý tự động; xem kết quả trong lịch sử.":
                        "Đơn đã được xử lý hoặc thay đổi trạng thái. Không tạo thêm email hủy.",service.history(reservationId));
    }
    @PostMapping("/trigger") public TriggerResult trigger(@PathVariable long reservationId,
            @RequestBody(required=false) TriggerRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        return service.trigger(reservationId,request!=null && Boolean.TRUE.equals(request.simulateFailure()),
                principal==null?null:principal.id());
    }
}
