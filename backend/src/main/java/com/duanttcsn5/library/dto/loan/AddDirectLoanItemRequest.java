package com.duanttcsn5.library.dto.loan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The current browser draft is only used for duplicate and quota checks. */
public record AddDirectLoanItemRequest(
        @NotBlank(message = "Vui lòng nhập mã thẻ thư viện.")
        @Size(max = 100, message = "Mã thẻ không được vượt quá 100 ký tự.") String cardNumber,
        @NotBlank(message = "Vui lòng nhập mã vạch sách.")
        @Size(max = 100, message = "Mã vạch không được vượt quá 100 ký tự.") String barcode,
        @NotNull(message = "Vui lòng gửi danh sách mã vạch đã nhập.")
        @Size(max = 10, message = "Danh sách lượt mượn không được vượt quá 10 sách.")
        List<@NotBlank(message = "Mã vạch trong danh sách không được để trống.")
             @Size(max = 100, message = "Mã vạch không được vượt quá 100 ký tự.") String> selectedBarcodes,
        boolean overridePreview
) {

    /** Keep older draft requests that omit overridePreview compatible with Jackson 3. */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public AddDirectLoanItemRequest(
            @JsonProperty("cardNumber") String cardNumber,
            @JsonProperty("barcode") String barcode,
            @JsonProperty("selectedBarcodes") List<String> selectedBarcodes,
            @JsonProperty("overridePreview") Boolean overridePreview) {
        this(cardNumber, barcode, selectedBarcodes, Boolean.TRUE.equals(overridePreview));
    }


    public AddDirectLoanItemRequest(String cardNumber, String barcode, List<String> selectedBarcodes) {
        this(cardNumber, barcode, selectedBarcodes, false);
    }
}
