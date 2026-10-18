package com.duanttcsn5.library.ai;

import java.util.*;
import java.util.regex.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** Book references derived only from previously verified tool blocks, never from model-provided IDs. */
final class AiConversationContext {
    private AiConversationContext() {}
    static List<Map<String,Object>> rememberedBooks(List<Block> blocks) {
        return blocks.stream().filter(b->"books".equals(b.type()))
            .filter(b->!b.items().isEmpty())
            .reduce((first,second)->second).map(b->b.items().stream().limit(6)
                .filter(x->x.get("id") instanceof Number && x.get("title") instanceof String)
                .map(x->Map.<String,Object>of("id",((Number)x.get("id")).longValue(),"title",(String)x.get("title")))
                .toList()).orElse(List.of());
    }
    private static final Pattern ORDINAL=Pattern.compile("(?:cuốn|quyển|sách)\\s*(?:thứ|số)\\s*(\\d{1,2}|một|hai|ba|bốn|năm|sáu)|(?:cuốn|quyển|sách)\\s*(đầu tiên|cuối cùng)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern REFERENCE=Pattern.compile("(?iu)(cuốn|quyển|sách)\\s*(đó|này|vừa nói|trên)|cuốn thứ|quyển thứ|sách thứ|cuốn số|quyển số|sách số|cuốn đầu|cuốn cuối");
    static boolean isPluralBookReference(String text) {
        return text!=null && text.toLowerCase(Locale.forLanguageTag("vi-VN"))
            .matches("(?s).*(những cuốn đó|các cuốn đó|mấy cuốn đó|các quyển đó|những quyển trên|cả [2-6] cuốn).*" );
    }
    static boolean isBookReference(String text) { return text!=null && REFERENCE.matcher(text).find(); }
    static Optional<Long> referencedBook(String text,List<Map<String,Object>> verifiedBooks) {
        if(text==null || verifiedBooks==null || verifiedBooks.isEmpty() || !REFERENCE.matcher(text).find()) return Optional.empty();
        var matcher=ORDINAL.matcher(text.toLowerCase(Locale.forLanguageTag("vi-VN")));
        int index=-1;
        if(matcher.find()) {
            String ordinal=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
            index=switch(ordinal) {
                case "một","đầu tiên" -> 0; case "hai" -> 1; case "ba" -> 2;
                case "bốn" -> 3; case "năm" -> 4; case "sáu" -> 5;
                case "cuối cùng" -> verifiedBooks.size()-1;
                default -> {try {yield Integer.parseInt(ordinal)-1;}catch(NumberFormatException e){yield -1;}}
            };
        } else if(verifiedBooks.size()==1) index=0;
        if(index<0 || index>=verifiedBooks.size())return Optional.empty();
        Object id=verifiedBooks.get(index).get("id");
        return id instanceof Number n && n.longValue()>0 ? Optional.of(n.longValue()) : Optional.empty();
    }
    /** Intent hints are derived from verified, role-scoped tool blocks and never contain PII. */
    static String lastDomain(List<Block> blocks) {
        for(int i=blocks.size()-1;i>=0;i--) {
            String type=blocks.get(i).type();
            if(type.equals("cardStatus") || type.equals("cardRecords")) return "cards";
            if(type.equals("books"))return "books";
            if(type.equals("loans"))return "loans";
            if(type.equals("reservations"))return "reservations";
        }
        return "";
    }
    static String cardStatus(String question,String previous) {
        String q=Objects.toString(question,"").toLowerCase(Locale.forLanguageTag("vi-VN"));
        if(q.contains("khóa") || q.contains("locked"))return "LOCKED";
        if(q.contains("hết hạn") || q.contains("expired"))return "EXPIRED";
        if(q.contains("ngừng") || q.contains("disabled"))return "DISABLED";
        if(q.contains("hoạt động") || q.contains("active"))return "ACTIVE";
        return previous==null?"":previous;
    }
    static String relevantQuestion(String question,String domain,String status) {
        // Follow-up phrases like 'trong số đó' provide no useful keyword to the tool selector.
        if(!Set.of("cards","books","loans","reservations").contains(domain))return question;
        String clean=Objects.toString(question,"").toLowerCase(Locale.forLanguageTag("vi-VN"));
        // Never pollute an unrelated new question with a topic from an older turn.
        if(!clean.matches("(?s).*(trong số đó|những .* đó|các .* đó|của ai|vậy |trường hợp nào|cần chú ý|bao nhiêu trong số|thế còn|còn chúng|những người đó).*"))
            return question;
        String extra=switch(domain) {
            case "cards" -> " thẻ thư viện " + (status.isBlank()?"":"trạng thái "+status);
            case "books" -> " sách";
            case "loans" -> " phiếu mượn";
            case "reservations" -> " đơn đặt giữ";
            default -> "";
        };
        return question+" "+extra;
    }
    static String memoryHint(String domain,String status) {
        if(!"cards".equals(domain))return "";
        return "\nNgữ cảnh ĐÃ XÁC MINH từ lượt trước: chủ đề là THẺ THƯ VIỆN"
            +(status.isBlank()?"":" và nhóm trạng thái "+status)
            +". Các cụm 'trong số đó', 'những thẻ đó', 'trường hợp nào' đề cập nhóm này, trừ khi người dùng đổi chủ đề."
            +" Khi hỏi số lượng dùng getLibraryCardStatusSummary; hỏi chủ thẻ dùng searchAuthorizedLibraryCards."
            +" KHÔNG suy đoán tên hoặc số liệu từ lịch sử, cần gọi công cụ mới.";
    }
    static String hint(List<Map<String,Object>> verifiedBooks) {
        if(verifiedBooks==null || verifiedBooks.isEmpty())return "";
        StringJoiner join=new StringJoiner("; ");
        for(int i=0;i<verifiedBooks.size();i++) {
            var b=verifiedBooks.get(i);
            join.add("Sách thứ "+(i+1)+": "+b.get("title"));
        }
        return "\nSách đã được truy vấn trong hội thoại (chỉ để nhận diện tham chiếu; cần tra cứu lại dữ liệu hiện tại): "+join;
    }
}
