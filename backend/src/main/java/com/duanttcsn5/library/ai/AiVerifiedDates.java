package com.duanttcsn5.library.ai;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Facts that can be answered deterministically without exposing a request to an LLM.
 * Lunar dates must be verified before they are added; unknown years are NOT guessed.
 */
public final class AiVerifiedDates {
    private AiVerifiedDates() {}
    private static final ZoneId VIETNAM=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final LocalDate TET_2027=LocalDate.of(2027,2,6);
    // https://baochinhphu.vn/nghi-tet-am-lich-2027-keo-dai-7-ngay-nghi-quoc-khanh-4-ngay-102261010095648278.htm
    // https://www.timeanddate.com/holidays/vietnam/tet-new-year
    static Optional<String> answer(String text, LocalDate today) {
        String lower=text.toLowerCase(Locale.forLanguageTag("vi-VN")).trim();
        if(lower.length()>180) return Optional.empty();
        if(lower.matches(".*(hôm nay|hom nay|ngày hôm nay).*?(thứ mấy|thu may|ngày mấy|ngày nào).*" ) ||
            lower.matches(".*(hôm nay|hom nay|nay) là (thứ|thu|ngày|ngay).*")) {
            String week=switch(today.getDayOfWeek()) {
                case MONDAY -> "Thứ Hai"; case TUESDAY -> "Thứ Ba"; case WEDNESDAY -> "Thứ Tư";
                case THURSDAY -> "Thứ Năm"; case FRIDAY -> "Thứ Sáu"; case SATURDAY -> "Thứ Bảy";
                case SUNDAY -> "Chủ nhật";
            };
            return Optional.of("Theo múi giờ Việt Nam, hôm nay là **"+week+", ngày "+today.format(DATE)+"**.");
        }
        if(lower.contains("tết") || lower.matches(".*\\btet\\b.*")) {
            if(lower.contains("trung thu") || lower.contains("thiếu nhi") || lower.contains("tết dương lịch")
                || lower.contains("lịch thư viện") || (lower.contains("thư viện") && lower.contains("mở cửa"))) return Optional.empty();
            if(lower.matches(".*20(2[89]|[3-9][0-9]).*")) return Optional.of("Tôi chưa có lịch âm đã kiểm chứng cho năm đó nên không đưa ra ngày dự đoán.");
            if(lower.contains("2026"))
                return Optional.of("Mùng 1 Tết Nguyên đán **2026** là **17/02/2026** (thứ Ba). Nguồn: timeanddate.com – lịch Tết Việt Nam.");
            if(today.isAfter(TET_2027) && !lower.contains("2027"))
                return Optional.of("Tôi chưa có ngày Tết tiếp theo đã được kiểm chứng từ nguồn tin cậy.");
            if(lower.contains("nghỉ") || lower.contains("nghi tet"))
                return Optional.of("Theo thông báo được Báo Chính phủ đăng ngày 10/10/2026, lịch nghỉ Tết âm lịch **2027** của khối cơ quan hành chính, đơn vị sự nghiệp công lập là **04/02–10/02/2027**. Lịch từng doanh nghiệp có thể khác.");
            long days=ChronoUnit.DAYS.between(today,TET_2027);
            if(lower.contains("bao lâu") || lower.contains("còn mấy") || lower.contains("còn bao") || lower.contains("đếm ngược")) {
                if(days>=0) return Optional.of("Mùng 1 **Tết Nguyên đán 2027** là **06/02/2027** (thứ Bảy). Tính từ "+today.format(DATE)+" còn **"+days+" ngày**. Nguồn đối chiếu: Báo Chính phủ, 10/10/2026 và timeanddate.com (lịch Tết Việt Nam).");
                return Optional.of("Mùng 1 Tết Nguyên đán 2027 đã qua (06/02/2027). Tôi chưa có mốc Tết tiếp theo đã kiểm chứng.");
            }
            return Optional.of("Mùng 1 **Tết Nguyên đán 2027** là **thứ Bảy, 06/02/2027**. Nguồn đối chiếu: Báo Chính phủ, 10/10/2026 và timeanddate.com.");
        }
        return Optional.empty();
    }
    public static Optional<String> answer(String text) { return answer(text,LocalDate.now(VIETNAM)); }
}
