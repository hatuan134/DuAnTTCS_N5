package com.duanttcsn5.library.ai;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Local admission policy, not an authorization boundary. Unknown requests ask for
 * clarification without calling a provider. Tool permissions still apply after admission.
 * Keep catalog SUBJECTS unrestricted (e.g. cooking/medicine/programming books).
 */
final class AiScopePolicy {
    enum Decision { ALLOW, CLARIFY, REFUSE }
    static final String REFUSAL = "LIBRA AI chỉ hỗ trợ tra cứu sách, mượn/trả, đặt giữ, thẻ, tài khoản và nghiệp vụ thư viện theo quyền của bạn. "
        + "Tôi không trả lời yêu cầu ngoài phạm vi này, kể cả câu hỏi ngày/Tết nói chung. "
        + "Bạn có thể hỏi tìm sách về chủ đề đó hoặc hỏi lịch hoạt động của thư viện.";
    static final String CLARIFICATION = "Bạn muốn tra cứu sách hay thực hiện nghiệp vụ nào trong LIBRA? "
        + "Hãy nói rõ sách, phiếu mượn, thẻ, tài khoản, báo cáo hoặc lịch thư viện để tôi hỗ trợ đúng.";
    private static final Pattern GREETING = p("^(xin chao|chao|hello|hi|cam on|thanks|thank you|tam biet|bye)"
        + "( (ban|nhe|nha|a|libra|ai|rat nhieu))*[!.,? ]*$|^(ban la ai|ban (co the )?(giup|ho tro|lam) (gi|duoc gi)|gioi thieu ban than)[?.! ]*$");
    private static final Pattern DOMAIN = p("\\b(sach|cuon|quyen|muon|tra sach|phieu|dat giu|ban doc|doc gia|the thu vien|"
        + "the bi khoa|the do|cac the|loai the|han the|han tra|cap the|gia han|qua han|no phi|mien giam|boi thuong|nhac han|nhac tra|"
        + "tai khoan|dang nhap|dang ky|mat khau|ho so|nhan vien|phan quyen|quyen truy cap|"
        + "thong ke|bao cao|nhat ky|kiem toan|audit|kho|ke|ban sao|tac gia|the loai|"
        + "mo tra cuu|chuc nang|tinh nang|huong dan su dung|dong cua|mo cua|"
        + "lich (nghi|thu vien)|ngay nghi|quy dinh|noi quy|nguong no|muc phi|he so|"
        + "thu phi|bien lai|thanh toan|nha xuat ban|isbn|barcode|bien muc|don huy|hang cho|kiem ke|xuat du lieu|tong quan|"
        + "books?|loans?|reservations?|catalog)\\b");
    private static final Pattern LIBRARY_INFO = p("^(?:hay |cho toi |giup toi )?(?:"
        + "gioi thieu (?:ve )?(?:thu vien|libra)|(?:thu vien|libra) (?:la gi|o dau|co (?:gi|mo cua khong))"
        + ")[?.! ]*$");
    // An operation outside library services is not made valid by adding "LIBRA".
    private static final Pattern OTHER_TASK = p("\\b(viet (code|ma|chuong trinh|bai van|tho|truyen|email)|"
        + "lap trinh|giai (toan|bai tap|phuong trinh)|tinh (dao ham|tich phan)|"
        + "dich (cau|doan|van ban)|nau an|cach nau|cong thuc nau|chuan doan|chan doan|tu van (y te|dau tu)|"
        + "du doan (bong da|ty so|chung khoan)|ke chuyen|write (code|a poem|an essay)|"
        + "solve|translate|ignore (all |the )?(system|instructions)|bo qua (chi dan|quy tac|gioi han)|"
        + "dong vai|roleplay)\\b");
    private static final Pattern GENERAL = p("\\b(thoi tiet|gia vang|ty gia|chung khoan|bong da|"
        + "thu do|tong thong|thu tuong|tu vi|xem boi|weather|bitcoin|ngan hang)\\b");
    private static final Pattern DATE = p("\\b(tet|hom nay.*(thu may|ngay may|ngay nao)|today|new year)\\b");
    private static final Pattern LIBRARY_DATE = p("\\b(lich|dong cua|mo cua|ngay nghi|gio lam viec|han tra|muon|tra sach|gia han|dat giu|nhac han)\\b");
    private static final Pattern CATALOG = p("\\b(tim|tra cuu|goi y|gioi thieu|co|search|find|recommend)\\b.*\\b(sach|cuon|quyen|book|books)\\b");
    private static final Pattern FOLLOW_UP = p("^(?:vay |the |the con |con )?(?:"
        + "trang (?:sau|tiep|truoc|\\d+)|xem (?:them|tiep)|tiep tuc|"
        + "(?:trong so do|nhung nguoi do|nhung the do|cac the do|chung|cua ai|cua toi|cua minh|"
        + "truong hop nao|co gi can chu y|tai sao|vi sao|nhu the nao|lam sao)"
        + "(?: (?:co|bao nhieu|bi|khoa|het han|con han|thuoc ve|la ai|ai|can|chu y|truong hop nao|thi sao|vay|a|nhe))*|"
        + "(?:thang|nam) (?:nay|truoc|sau|\\d{1,4})(?: nam \\d{4})?|"
        + "(?:con|co) (?:khong|bao nhieu|loai khac khong)|\\d{1,4})[?.! ]*$");

    private AiScopePolicy() {}
    private static Pattern p(String value) { return Pattern.compile(value); }
    static boolean isFollowUp(String value) { return FOLLOW_UP.matcher(normalize(value)).matches(); }
    static String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D')
            .toLowerCase(Locale.ROOT).replaceAll("[\\p{Cf}\\p{Cc}]", " ")
            .replaceAll("\\s+", " ").trim();
    }
    static Decision decide(String text, String verifiedDomain) {
        String q = normalize(text);
        if (GREETING.matcher(q).matches()) return Decision.ALLOW;
        // Check each independently requested task; a catalog lookup cannot bless a second task.
        String[] clauses = q.split("(?:[;!?\\n]+|\\.(?:\\s|$)|\\b(?:va|roi|dong thoi|ngoai ra|and|also)\\b)");
        boolean admitted = false;
        for (String raw : clauses) {
            String part = raw.trim();
            if (part.isEmpty() || GREETING.matcher(part).matches()) continue;
            boolean catalog = CATALOG.matcher(part).find() || part.matches("^(sach|cuon|quyen|books?)\\s+.*");
            // Topic words after "sách về ..." are data, not an instruction to perform that task.
            String operations = catalog ? part.replaceFirst("\\b(sach|cuon|quyen|books?)\\s+"
                + "(?:(?:ve|cach|huong dan|day|about|on|how to)\\s+)*" + OTHER_TASK.pattern(), "catalog subject") : part;
            if (OTHER_TASK.matcher(operations).find()) return Decision.REFUSE;
            if (!catalog && (GENERAL.matcher(part).find()
                || part.matches(".*\\d\\s*[+*=]\\s*\\d.*"))) return Decision.REFUSE;
            if (!catalog && DATE.matcher(part).find()
                && !(DOMAIN.matcher(part).find() && LIBRARY_DATE.matcher(part).find()))
                return Decision.REFUSE;
            if (DOMAIN.matcher(part).find() || LIBRARY_INFO.matcher(part).matches()) { admitted = true; continue; }
            if (admitted && part.matches("(?:bi khoa|het han|con han|dang hoat dong|lich su|van hoc)(?:[?.! ]*)"))
                continue;
            if (admitted && part.matches("(?:hien thi |xem )?(?:bieu do|bang so lieu)(?: theo ngay)?[. ]*"))
                continue;
            if (!Objects.toString(verifiedDomain, "").isBlank() && FOLLOW_UP.matcher(part).matches()) {
                admitted = true; continue;
            }
            // Unknown semantics are never sent to an AI just because the UI is on a library page.
            return Decision.CLARIFY;
        }
        return admitted ? Decision.ALLOW : Decision.CLARIFY;
    }
    /** Filter both live and resumed history: rejected/legacy off-topic turns stay visible only in UI. */
    static List<Map<String,Object>> providerHistory(List<Map<String,Object>> history, String domain) {
        List<Map<String,Object>> safe = new ArrayList<>();
        boolean include = false;
        for (var entry : history) {
            if ("user".equals(entry.get("role")))
                include = decide(Objects.toString(entry.get("content"), ""), domain) == Decision.ALLOW;
            if (include) safe.add(entry);
        }
        return safe;
    }
}
