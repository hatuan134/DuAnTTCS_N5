package com.duanttcsn5.library.dto.loan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The confirmation uses the existing card/date/override contract. */
public record CreateReservationLoanBatchRequest(
        @NotNull(message = "Vui lòng chọn các đơn đặt giữ cần nhận.")
        @Size(min = 1, max = 3, message = "Mỗi lượt nhận gồm từ 1 đến 3 đơn đặt giữ.")
        List<@NotNull(message = "Mã đơn đặt giữ không được để trống.")
             @Positive(message = "Mã đơn đặt giữ phải là số dương.") Long> reservationIds,
        @NotNull(message = "Vui lòng nhập thông tin đối chiếu thẻ.")
        @Valid CreateReservationLoanRequest confirmation
) {}
