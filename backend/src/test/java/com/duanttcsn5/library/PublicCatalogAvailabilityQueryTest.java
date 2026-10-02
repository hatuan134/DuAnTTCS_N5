package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.BookCopyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.Query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Chạy truy vấn thật của repository trên PostgreSQL, sử dụng bảng TEMP cùng tên.
 * Không ghi/sửa dữ liệu dự án. Bỏ qua nếu chưa đặt S2_05_2_DB_URL.
 */
@EnabledIfEnvironmentVariable(named = "S2_05_2_DB_URL", matches = ".+")
class PublicCatalogAvailabilityQueryTest {
    @Test void onlyAvailableCopiesWithoutOpenLoansAreCounted() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                System.getenv("S2_05_2_DB_URL"),
                System.getenv().getOrDefault("DB_USERNAME", "postgres"),
                System.getenv("DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TEMP TABLE book_copies (id BIGINT, book_id BIGINT, status VARCHAR(30)) ON COMMIT DROP");
                    statement.execute("CREATE TEMP TABLE loan_items (book_copy_id BIGINT, returned_at TIMESTAMPTZ) ON COMMIT DROP");
                    statement.execute("""
                            INSERT INTO book_copies VALUES
                            (1, 101, 'AVAILABLE'), (2, 101, 'AVAILABLE'),
                            (3, 101, 'BORROWED'), (4, 101, 'HELD'),
                            (5, 101, 'REPAIR'), (6, 101, 'REMOVED'),
                            (7, 101, 'LOST'), (8, 101, 'DAMAGED'),
                            (9, 101, 'AVAILABLE'),
                            (10, 102, 'BORROWED'), (11, 102, 'HELD'),
                            (12, 102, 'REPAIR'), (13, 102, 'REMOVED'),
                            (14, 103, 'AVAILABLE')
                            """);
                    // Bản 9 có trạng thái cũ AVAILABLE nhưng đang mượn; bản 2 đã trả.
                    statement.execute("INSERT INTO loan_items VALUES (9, NULL), (2, CURRENT_TIMESTAMP)");
                }
                String groupedSql = BookCopyRepository.class.getMethod("countAvailableGroupedByBookId")
                        .getAnnotation(Query.class).value();
                Map<Long, Long> counts = new HashMap<>();
                try (var statement = connection.createStatement(); var result = statement.executeQuery(groupedSql)) {
                    while (result.next()) counts.put(result.getLong(1), result.getLong(2));
                }
                assertEquals(Map.of(101L, 2L, 103L, 1L), counts);

                String singleSql = BookCopyRepository.class.getMethod("countAvailableByBookId", Long.class)
                        .getAnnotation(Query.class).value().replace(":bookId", "?");
                try (var statement = connection.prepareStatement(singleSql)) {
                    statement.setLong(1, 102L);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        assertEquals(0L, result.getLong(1));
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
