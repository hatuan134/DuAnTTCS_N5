package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.reader.ReaderLoanHistoryResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** CSV serialization only; ownership, filtering and late-return rules remain in the existing service. */
public final class ReaderLoanHistoryCsv {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private ReaderLoanHistoryCsv() {}

    public record Export(String filename, byte[] content, int rowCount) {}

    public static Export create(ReaderLoanHistoryResponse history, OffsetDateTime exportedAt) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        append(csv, "Mã Bạn đọc", "Mã phiếu", "Tên sách", "Mã vạch bản sao",
                "Ngày mượn (giờ Việt Nam)", "Hạn trả", "Ngày trả thực tế (giờ Việt Nam)",
                "Trạng thái phiếu", "Phiếu từng trả trễ", "Bản sao trả trễ");
        int count = 0;
        // Existing repository orders loan headers by borrowed_at DESC, id DESC and items by id ASC.
        for (var loan : history.loans()) {
            if (loan.items().isEmpty()) {
                append(csv, history.profile().memberCode(), loan.loanNumber(), "", "",
                        timestamp(loan.borrowedAt()), "", "", status(loan.status()),
                        loan.returnedLate() ? "Có" : "Không", "");
                count++;
            } else {
                for (var item : loan.items()) {
                    append(csv, history.profile().memberCode(), loan.loanNumber(), item.bookTitle(), item.barcode(),
                            timestamp(item.borrowedAt()), item.dueAt() == null ? "" : DATE.format(item.dueAt().atZoneSameInstant(VIETNAM)),
                            timestamp(item.returnedAt()), status(loan.status()),
                            loan.returnedLate() ? "Có" : "Không", item.returnedLate() ? "Có" : "Không");
                    count++;
                }
            }
        }
        String member = history.profile().memberCode();
        String safeMember = member == null ? "reader" : member.replaceAll("[^A-Za-z0-9_-]", "_");
        String filename = "lich-su-muon-tra_" + safeMember + "_" + history.profile().userId()
                + "_" + FILE_TIME.format(exportedAt.atZoneSameInstant(VIETNAM)) + ".csv";
        return new Export(filename, csv.toString().getBytes(StandardCharsets.UTF_8), count);
    }

    private static String timestamp(OffsetDateTime value) {
        return value == null ? "" : TIMESTAMP.format(value.atZoneSameInstant(VIETNAM));
    }

    private static String status(String value) {
        if (value == null) return "";
        return switch (value) {
            case "BORROWED" -> "Đang mượn";
            case "PARTIALLY_RETURNED" -> "Đã trả một phần";
            case "RETURNED" -> "Đã trả";
            case "EMPTY" -> "Chưa có bản sao";
            default -> value;
        };
    }

    private static void append(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            String value = values[i] == null ? "" : values[i];
            // Quoting alone does not stop spreadsheet formulas in user-controlled book titles/codes.
            String leading = value.stripLeading();
            if ((!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0)
                    || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
