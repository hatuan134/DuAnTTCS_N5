package com.duanttcsn5.library.ai;

import java.util.*;
import java.time.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** Friendly answers composed exclusively from current, authorized tool results.
 * For private data, no record values are sent to an outside model just to generate prose.
 */
final class AiNaturalAnswer {
    private AiNaturalAnswer() {}
    static String compose(String question,List<Block> blocks) {
        var parts=new ArrayList<String>();
        for(var b:blocks) {
            if("notice".equals(b.type()))continue;
            var content=switch(b.type()) {
                case "books" -> books(question,b);
                case "overview" -> overview(b);
                case "statistics" -> AiChatService.verifiedStatisticsSummary(b);
                case "loans" -> list(b,"phiếu/sách mượn");
                case "reservations" -> list(b,"đơn đặt giữ");
                case "copies" -> list(b,"bản sao sách");
                case "audit" -> list(b,"sự kiện");
                case "records" -> list(b,"mục");
                case "libraryCard" -> card(b);
                case "cardStatus" -> cardStatus(question,b);
                case "cardRecords" -> cardRecords(question,b);
                case "libraryCalendar" -> calendar(b);
                case "settings" -> settings(b);
                case "navigation" -> navigation(b);
                default -> "Tôi đã tra cứu được dữ liệu. Bạn có thể nhấn xem chi tiết bên dưới.";
            };
            if(!content.isBlank())parts.add(content);
        }
        return String.join("\n\n",parts);
    }
    /** Aggregate facts are phrased from verified records; no personal data reaches a model. */
    private static String cardStatus(String question,Block b) {
        Map<String,Long> counts=new HashMap<>();
        for(var r:b.items()) if(r.get("value") instanceof Number n) counts.put(clean(r.get("status")),n.longValue());
        long total=total(b);
        String q=Objects.toString(question,"").toLowerCase(Locale.forLanguageTag("vi-VN"));
        if(q.matches("(?s).*(khóa|locked).*")) {
            return "Trong **"+total+" thẻ thư viện** hiện có, **"+counts.getOrDefault("LOCKED",0L)+" thẻ đang bị khóa**. "
                +"Tôi có thể kiểm tra tiếp danh sách chủ thẻ nếu tài khoản của bạn được phép xem thông tin đó.";
        }
        if(q.matches("(?s).*(cần chú ý|bất thường|rủi ro|ưu tiên|xử lý).*")) {
            return "Theo trạng thái thẻ mới nhất, LIBRA có **"+counts.getOrDefault("LOCKED",0L)+" thẻ bị khóa**, **"
                +counts.getOrDefault("EXPIRED",0L)+" thẻ hết hạn** và **"+counts.getOrDefault("DISABLED",0L)
                +" thẻ ngừng hoạt động**. Đây là các nhóm nên được rà soát; riêng trạng thái không đủ để khẳng định thẻ nào có vấn đề khẩn cấp. "
                +"Bạn muốn xem nhóm nào trước?";
        }
        return "Tôi vừa kiểm tra **"+total+" thẻ thư viện** trên hệ thống: **"
            +counts.getOrDefault("ACTIVE",0L)+" đang hoạt động**, **"
            +counts.getOrDefault("LOCKED",0L)+" bị khóa**, **"
            +counts.getOrDefault("EXPIRED",0L)+" hết hạn** và **"
            +counts.getOrDefault("DISABLED",0L)+" ngừng hoạt động**. Bạn có thể hỏi tiếp về bất kỳ nhóm nào.";
    }
    /** Reader names are rendered ONLY server-side for already-authorized staff. */
    private static String cardRecords(String question,Block b) {
        long total=total(b);
        String status=clean(b.meta().get("status"));
        if(total==0) return status.isBlank()?"Tôi không tìm thấy thẻ thư viện phù hợp.":"Hiện không có thẻ thư viện nào ở trạng thái **"+status+"**.";
        StringBuilder out=new StringBuilder("Tôi đã xác minh có **"+total+" thẻ thư viện**")
            .append(status.isBlank()?" trong danh sách được phép xem.":" ở trạng thái **"+status+"**.");
        for(var row:b.items().stream().limit(5).toList()) {
            String name=clean(row.get("title"));
            String number=clean(row.get("cardNumber"));
            out.append("\n- ").append(name.isBlank()?"Chủ thẻ chưa có tên": "**"+name+"**");
            if(!number.isBlank()) out.append(" — mã thẻ ").append(number);
            out.append(".");
        }
        if(total>5) out.append("\nDanh sách còn các thẻ khác. Bạn có thể xem trong quản lý thẻ.");
        return out.append("\n\nTôi chỉ hiển thị dữ liệu mà tài khoản nhân viên hiện tại được phép truy cập.").toString();
    }

    private static String books(String question,Block b) {
        if(b.items().isEmpty()) {
            Object published=b.meta().get("publicationYear");
            String year=published instanceof Number ? " xuất bản năm "+published : "";
            return "Tôi đã kiểm tra dữ liệu thư viện nhưng chưa tìm thấy đầu sách nào"+year+" phù hợp. Bạn có muốn tìm theo năm khác hoặc chủ đề cụ thể hơn không?";
        }
        String q=question==null?"":question.toLowerCase(Locale.forLanguageTag("vi-VN"));
        if(b.items().size()==1) {
            var row=b.items().get(0);
            String title=clean(row.get("title"));
            if(q.matches("(?s).*(nội dung|nói về|giới thiệu|tóm tắt).*")) {
                String summary=clean(row.get("summary"));
                return summary.isBlank()
                    ? "Tôi đã tìm thấy **"+title+"** nhưng dữ liệu LIBRA chưa có phần mô tả nội dung cho cuốn này. Bạn có thể mở chi tiết sách để xem thông tin khác."
                    : "**"+title+"** có phần giới thiệu trong thư viện như sau: "+summary+"\n\nBạn muốn biết tình trạng mượn của cuốn này không?";
            }
            if(q.matches("(?s).*(tác giả|ai viết).*"))
                return "Cuốn **"+title+"** được ghi nhận với tác giả **"+clean(row.get("authorName"))+"** trong LIBRA. Bạn muốn xem chi tiết sách không?";
            if(q.matches("(?s).*(còn|sẵn sàng|mượn được|có bản).*" ) && row.get("availableCount") instanceof Number n)
                return "Tôi vừa kiểm tra lại **"+title+"**: hiện "+(n.longValue()>0?"có **"+n.longValue()+" bản sẵn sàng** để mượn.":"**chưa có bản sẵn sàng** để mượn.")+" Bạn muốn xem chi tiết hoặc tìm sách tương tự không?";
        }
        StringBuilder answer=new StringBuilder("Tôi đã tìm thấy ");
        long total=total(b); answer.append(total>=0?total:b.items().size()).append(" đầu sách phù hợp trong LIBRA.");
        int size=Math.min(3,b.items().size());
        for(int i=0;i<size;i++) {
            var row=b.items().get(i);String title=clean(row.get("title"));
            answer.append("\n- **").append(title).append("**");
            String author=clean(row.get("authorName"));
            if(!author.isBlank())answer.append(" — ").append(author);
            Object available=row.get("availableCount");
            if(available instanceof Number n)answer.append(" (sẵn sàng: ").append(n.longValue()).append(" bản)");
            answer.append(".");
        }
        if(b.items().size()>size)answer.append("\nCòn các kết quả khác trong danh sách chi tiết.");
        return answer.append(b.items().size()>1
            ? "\n\nBạn muốn tôi kiểm tra kỹ cuốn nào? Bạn có thể hỏi ‘cuốn thứ hai còn không?’"
            : "\n\nBạn muốn xem chi tiết cuốn sách này hay tìm thêm sách cùng chủ đề?").toString();
    }
    private static String overview(Block b) {
        if(b.items().isEmpty())return "Chưa lấy được số liệu tổng quan. Bạn thử lại sau nhé.";
        StringBuilder s=new StringBuilder("Tôi đã kiểm tra **").append(clean(b.title())).append("** theo dữ liệu hiện có của LIBRA:\n");
        for(var row:b.items())if(row.get("value") instanceof Number n)
            s.append("- **").append(clean(row.get("title"))).append(":** ").append(n.longValue()).append("\n");
        return s.append("\nBạn có thể mở phần chi tiết hoặc hỏi thêm về từng chỉ số.").toString();
    }
    private static String list(Block b,String unit) {
        long total=total(b); if(total<0)total=b.items().size();
        if(total==0)return "Tôi đã kiểm tra **"+clean(b.title())+"** và xác nhận có **0 kết quả** phù hợp. Hiện chưa có "+unit+" nào trong phạm vi tra cứu này.";
        StringBuilder out=new StringBuilder("Tôi đã tra cứu **").append(clean(b.title()))
            .append("** và ghi nhận **").append(total).append(" ").append(unit).append("** phù hợp.");
        int max=Math.min(b.items().size(),3);
        for(int i=0;i<max;i++) {
            var row=b.items().get(i); String title=clean(row.get("title"));
            out.append("\n- **").append(title.isBlank()?"Mục "+(i+1):title).append("**");
            String status=clean(row.get("status"));if(!status.isBlank())out.append(" — ").append(status);
            Object value=row.get("value");if(value instanceof Number n)out.append(" (số đầu sách: ").append(n.longValue()).append(")");
            out.append(".");
        }
        out.append("\n\nBạn có thể nhấn **Xem chi tiết** để xem đầy đủ danh sách, hoặc hỏi tôi về một mục cụ thể.");
        return out.toString();
    }
    private static String calendar(Block b) {
        StringBuilder result=new StringBuilder("Tôi vừa kiểm tra **lịch hoạt động của thư viện trong năm ")
            .append(clean(b.meta().get("year"))).append("** theo cấu hình LIBRA.\n\n");
        var weekly=b.items().stream().filter(r->"weekly".equals(r.get("kind"))).toList();
        if(!weekly.isEmpty()) {
            var closedWeekdays=weekly.stream().filter(r->Boolean.FALSE.equals(r.get("open")))
                .map(r->clean(r.get("title"))).toList();
            if(closedWeekdays.isEmpty())result.append("**Lịch hằng tuần:** Không có ngày nghỉ cố định được cấu hình.\n");
            else result.append("**Ngày nghỉ hằng tuần:** ").append(String.join(", ",closedWeekdays)).append(".\n");
            var openDays=weekly.stream().filter(r->Boolean.TRUE.equals(r.get("open"))).toList();
            if(!openDays.isEmpty()) {
                result.append("**Giờ mở cửa:**\n");
                for(var r:openDays)result.append("- ").append(clean(r.get("title"))).append(": ")
                    .append(clean(r.get("openTime"))).append("–").append(clean(r.get("closeTime"))).append(".\n");
            }
        }
        var days=b.items().stream().filter(r->"special".equals(r.get("kind"))).toList();
        Object total=b.meta().get("totalClosedDates");
        if(total instanceof Number n) result.append("\n**Các ngày đóng cửa riêng được khai báo trong năm:** ").append(n.longValue()).append(".\n");
        boolean pastYear=b.meta().get("year") instanceof Number y && y.intValue()<LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        if(days.isEmpty()) result.append(pastYear?"Không có ngày đóng cửa riêng được liệt kê trong năm đã chọn.":"Chưa có ngày đóng cửa riêng nào sắp tới được ghi nhận trong năm đang tra cứu.");
        else {
            result.append(pastYear?"**Những ngày đóng cửa đã ghi nhận:**\n":"**Những ngày đóng cửa sắp tới:**\n");
            for(var d:days.stream().limit(6).toList()) result.append("- ").append(clean(d.get("closedDate")))
                .append(clean(d.get("reason")).isBlank()?"":": "+clean(d.get("reason"))).append(".\n");
            if(days.size()>6 || (b.meta().get("upcomingClosedDates") instanceof Number n && n.longValue()>6))
                result.append("Bạn có thể mở lịch để xem các ngày còn lại.\n");
        }
        return result.toString().trim();
    }
    private static String settings(Block b) {
        Object threshold=b.meta().get("thresholdVnd");
        if(!(threshold instanceof Number n)) return "Tôi chưa đọc được ngưỡng nợ phí hiện tại. Bạn hãy thử lại sau.";
        String amount=String.format(Locale.forLanguageTag("vi-VN"),"%,d",n.longValue());
        return "Theo **cấu hình LIBRA hiện tại**, ngưỡng nợ phí để hệ thống **đề xuất khóa thẻ** là **"+amount+" đồng**. "
            +"Đây là ngưỡng đề xuất, không có nghĩa thẻ sẽ tự động bị khóa ngay khi chạm ngưỡng. "
            +"Bạn có thể mở trang cấu hình để kiểm tra hoặc thay đổi theo quyền được cấp.";
    }
    private static String navigation(Block b) {
        String guide=b.items().isEmpty()?"":clean(b.items().get(0).get("text"));
        return "Bạn có thể sử dụng **"+clean(b.title())+"** ngay trong LIBRA. "
            +(guide.isBlank()?"Nhấn nút mở chức năng bên dưới.":guide+" Nhấn nút bên dưới để đến đúng màn hình.");
    }
    private static String card(Block b) {
        if(b.items().isEmpty())return "Tôi chưa tìm được thông tin thẻ thư viện của bạn.";
        var r=b.items().get(0);
        String status=clean(r.get("status"));String expiry=clean(r.get("expiresAt"));
        return "Tôi đã kiểm tra thẻ thư viện của bạn."+(status.isBlank()?"":" Trạng thái hiện tại là **"+status+"**.")+
            (expiry.isBlank()?"":" Hạn sử dụng được ghi nhận: **"+expiry+"**.")+
            " Bạn có thể mở chi tiết thẻ ngay bên dưới để kiểm tra thêm.";
    }
    private static long total(Block b) { Object n=b.meta().get("total");return n instanceof Number v && v.longValue()>=0?v.longValue():-1; }
    private static String clean(Object x) {return Objects.toString(x,"").replaceAll("[\r\n*`\\[\\]<>]"," ").trim();}
}
