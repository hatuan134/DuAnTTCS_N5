package com.duanttcsn5.library.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.text.NumberFormat;
import java.util.Locale;

/** Inline table layout and escaped dynamic text; no website CSS or scripts. */
public final class DepositEmailContent {
    private DepositEmailContent() {}
    private static String escape(String value) {return ReadyPickupNotificationService.escape(value);}
    private static String money(JsonNode value) {
        return value==null || value.isNull() || value.isMissingNode()?"Chưa xác định":NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value.decimalValue())+" VND";
    }
    private static String line(String label,String value) {return "<p style=\"margin:6px 0;overflow-wrap:anywhere;word-break:break-word;\">"+escape(label)+": <strong>"+escape(value)+"</strong></p>";}
    public static String subject(String type,String json) {
        var data=new ObjectMapper().readTree(json);
        return switch(type) {
            case "DEPOSIT_RESERVATION" -> "LIBRA — Đặt giữ thành công và tiền cọc dự kiến";
            case "DEPOSIT_LOAN" -> "LIBRA — Xác nhận bàn giao sách và tiền cọc";
            case "DEPOSIT_RETURN" -> "LIBRA — Xác nhận nhận trả và đối soát tiền cọc";
            default -> "LIBRA — "+switch(data.path("kind").asText()) {case "REFUND"->"Đã xác nhận hoàn cọc";case "DEDUCT"->"Đã xác nhận khấu trừ cọc";default->"Đã xác nhận thu cọc";};
        };
    }
    public static String estimate(String json) {
        if(json==null)return "";
        var data=new ObjectMapper().readTree(json);
        if(!data.path("policyActive").asBoolean())return "<p style=\"margin:16px 0;padding:14px;background:#eff6ff;\"><strong>Tiền cọc cần chuẩn bị:</strong> Chưa có chính sách cọc có hiệu lực. Thư viện chưa yêu cầu cọc theo chính sách mới. Mượn sách miễn phí.</p>";
        String rate=data.path("percentage").isNull()?"Chưa xác định":data.path("percentage").asText()+"%";
        return "<div style=\"margin:16px 0;padding:14px;background:#eff6ff;overflow-wrap:anywhere;\">"
            +line("Tiền cọc cần chuẩn bị",money(data.path("requiredVnd")))
            +line("Giá bìa",money(data.path("coverPrice")))+line("Tỷ lệ cọc dự kiến",rate)
            +"<p>Mượn sách miễn phí. Chưa cần thanh toán khi đặt giữ; cọc được thu tại quầy trước bàn giao. Đây là số dự kiến, chưa phải tiền đã thanh toán.</p>"
            +"<p>Cọc từng cuốn được đối soát khi trả sách; nghĩa vụ hợp lệ có thể được khấu trừ. Tiền hoàn thực tế cần nhân viên xác nhận. Nếu chưa có giá bìa/bản sao, thư viện sẽ xác định số cọc trước khi bàn giao.</p></div>";
    }
    public static String render(String reader,String type,String json) {
        var data=new ObjectMapper().readTree(json);boolean estimate="DEPOSIT_RESERVATION".equals(type);
        var body=new StringBuilder();long total=0,collected=0,refunded=0;boolean complete=true;
        if(data.hasNonNull("loanNumber"))body.append(line("Phiếu mượn",data.path("loanNumber").asText()));
        if(data.hasNonNull("transactionNumber"))body.append(line("Mã giao dịch",data.path("transactionNumber").asText())).append(line("Số tiền giao dịch đã xác nhận",money(data.path("totalVnd"))));
        for(var item:data.path("items")) {
            body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"table-layout:fixed;width:100%;margin:12px 0;border:1px solid #e2e8f0;\"><tr><td style=\"padding:14px;overflow-wrap:anywhere;word-break:break-word;\">")
                .append(line("Sách",item.path("bookTitle").asText())).append(line("Mã vạch",item.path("barcode").asText("Chưa phân bổ bản sao")))
                .append(line("Giá bìa",money(item.path("coverPrice"))))
                .append(line("Tỷ lệ cọc",item.path("percentage").isNull()?"Chưa xác định":item.path("percentage").asText()+"%"))
                .append(line(estimate?"Cọc dự kiến":"Cọc của cuốn sách",money(item.path("requiredVnd"))));
            if(item.path("requiredVnd").isNumber())total=Math.addExact(total,item.path("requiredVnd").asLong());else complete=false;
            if(!estimate) {
                collected=Math.addExact(collected,item.path("collectedVnd").asLong());
                refunded=Math.addExact(refunded,item.path("refundedVnd").asLong());
                for(String[] field:new String[][]{{"loanNumber","Phiếu mượn"},{"borrowedAt","Ngày mượn"},{"dueAt","Hạn trả"},{"returnedAt","Ngày nhận trả"},{"transactions","Mã giao dịch cọc"}})
                    if(item.hasNonNull(field[0]))body.append(line(field[1],item.path(field[0]).asText()));
                for(String[] field:new String[][]{{"collectedVnd","Cọc đã thu"},{"heldVnd","Cọc đang giữ"},{"overdueVnd","Phí trễ hạn đã ghi nhận"},{"compensationVnd","Bồi thường đã ghi nhận"},{"deductedVnd","Cọc đã khấu trừ"},{"refundedVnd","Tiền đã hoàn thực tế"},{"outstandingVnd","Nghĩa vụ còn phải thanh toán"}})
                    body.append(line(field[1],money(item.path(field[0]))));
            }
            body.append("</td></tr></table>");
        }
        body.append(line(estimate?"Tổng cọc dự kiến":"Tổng cọc của các cuốn trong thông báo",complete?NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(total)+" VND":"Chưa xác định đầy đủ; thư viện sẽ thông báo trước bàn giao"));
        if(!estimate)body.append(line("Tổng cọc đã thu",NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(collected)+" VND"))
            .append(line("Tổng tiền đã hoàn thực tế",NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(refunded)+" VND"));
        String notice=estimate?"Chưa cần thanh toán ngay khi đặt giữ. Chuẩn bị cọc khi nhận sách; thông báo này không xác nhận đã thu tiền."
            :"Các số tiền trên là snapshot tại thời điểm nghiệp vụ. Nhận trả sách không có nghĩa đã hoàn tiền. Khoản đang giữ được đối soát theo từng cuốn; chỉ mục Tiền đã hoàn thực tế phản ánh tiền hoàn đã được xác nhận.";
        return "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"
            +"<body style=\"margin:0;background:#f1f5f9;font:14px/1.6 Arial,sans-serif;color:#1e293b;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;table-layout:fixed;\"><tr><td align=\"center\">"
            +"<table role=\"presentation\" width=\"100%\" style=\"width:100%;max-width:640px;table-layout:fixed;background:#fff;\"><tr><td style=\"padding:20px;background:#1e3a8a;color:#fff;\"><h1 style=\"font-size:22px;margin:0;\">LIBRA</h1><p>Mượn sách hoàn toàn miễn phí</p></td></tr>"
            +"<tr><td style=\"padding:16px;overflow-wrap:anywhere;word-break:break-word;\"><h2 style=\"font-size:18px;\">"+escape(subject(type,json))+"</h2>"
            +line("Bạn đọc",reader)+body+"<p style=\"padding:12px;background:#eff6ff;\">"+escape(notice)+"</p></td></tr></table></td></tr></table></body></html>";
    }
}
