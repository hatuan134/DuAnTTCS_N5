package com.duanttcsn5.library;
import com.duanttcsn5.library.service.DepositEmailContent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepositEmailContentTest {
    @Test void readyEstimateShowsAmountFirstAndExplainsInactivePolicy() {
        String html=DepositEmailContent.estimate("{\"policyActive\":true,\"coverPrice\":100000,\"percentage\":50,\"requiredVnd\":50000}");
        assertTrue(html.contains("Tiền cọc cần chuẩn bị: <strong>50.000 VND</strong>"));
        assertTrue(html.indexOf("Tiền cọc cần chuẩn bị")<html.indexOf("Giá bìa"));
        assertTrue(DepositEmailContent.estimate("{\"policyActive\":false}").contains("Chưa có chính sách cọc có hiệu lực"));
    }
    final String snapshot="""
        {"items":[{"bookTitle":"<script>alert(1)</script> Một tên sách rất dài", "barcode":"BC-1",
        "coverPrice":100000,"percentage":50,"requiredVnd":50000,"collectedVnd":50000,"heldVnd":35000,
        "overdueVnd":15000,"compensationVnd":0,"deductedVnd":15000,"refundedVnd":0,"outstandingVnd":0}]}
        """;
    @Test void returnedEmailNeverClaimsPendingRefundWasPaidAndEscapesDynamicText() {
        var html=DepositEmailContent.render("Reader <img src=x>","DEPOSIT_RETURN",snapshot);
        assertFalse(html.contains("<script>"));assertFalse(html.contains("<img src=x>"));
        assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("&lt;img src=x&gt;"));
        assertTrue(html.contains("Tiền đã hoàn thực tế: <strong>0 VND</strong>"));
        assertTrue(html.contains("Cọc đang giữ: <strong>35.000 VND</strong>"));
        assertTrue(html.contains("không có nghĩa đã hoàn tiền"));
        assertTrue(html.contains("max-width:640px"));assertTrue(html.contains("overflow-wrap:anywhere"));
        assertTrue(html.contains("table-layout:fixed"));assertFalse(html.contains("<script"));
    }
    @Test void reservationAndReadyMessagesAreEstimatesNotCashConfirmations() {
        var html=DepositEmailContent.render("Reader","DEPOSIT_RESERVATION",snapshot);
        assertTrue(html.contains("Tổng cọc dự kiến: <strong>50.000 VND</strong>"));
        assertTrue(html.contains("không xác nhận đã thu tiền"));assertFalse(html.contains("Cọc đã thu:"));
        var estimate=DepositEmailContent.estimate("{\"policyActive\":true,\"coverPrice\":null,\"percentage\":50,\"requiredVnd\":null}");
        assertTrue(estimate.contains("Chưa xác định"));assertFalse(estimate.contains("0 VND"));
        assertTrue(estimate.contains("chưa phải tiền đã thanh toán"));
    }
    @Test void actualRefundAndCollectionUseSeparateSubjectsAndTransactionNumbers() {
        var refund="{\"kind\":\"REFUND\",\"transactionNumber\":\"COC-7\",\"totalVnd\":35000,\"items\":[]}";
        assertTrue(DepositEmailContent.subject("DEPOSIT_TRANSACTION",refund).contains("Đã xác nhận hoàn cọc"));
        assertTrue(DepositEmailContent.render("Reader","DEPOSIT_TRANSACTION",refund).contains("COC-7"));
        assertTrue(DepositEmailContent.subject("DEPOSIT_TRANSACTION",refund.replace("REFUND","COLLECT")).contains("Đã xác nhận thu cọc"));
    }
}
