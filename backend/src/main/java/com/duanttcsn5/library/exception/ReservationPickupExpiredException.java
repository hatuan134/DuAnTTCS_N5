package com.duanttcsn5.library.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

/** Expected rejection AFTER expiry/release has been persisted in the owning transaction. */
public class ReservationPickupExpiredException extends ApiException {
    public static final String MESSAGE =
            "Đơn đặt giữ đã quá hạn nhận. Không thể lập phiếu mượn. Vui lòng yêu cầu bạn đọc đặt giữ lại.";

    public ReservationPickupExpiredException(Long reservationId) {
        super(HttpStatus.CONFLICT, "RESERVATION_PICKUP_EXPIRED", MESSAGE,
                Map.of("reservationId", reservationId, "status", "EXPIRED"));
    }
}
