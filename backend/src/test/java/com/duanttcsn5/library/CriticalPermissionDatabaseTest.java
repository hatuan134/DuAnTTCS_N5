package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.service.PermissionReviewCatalog;
import com.duanttcsn5.library.service.PermissionReviewInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Explicit opt-in, disposable PostgreSQL only. Real login, signed JWT, real SQL; all fixtures roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_09_1_DB_TEST", matches = "true")
@Transactional
class CriticalPermissionDatabaseTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired PermissionReviewCatalog catalog;
    @Autowired PermissionReviewInspector inspector;
    MockMvc mvc;
    Map<String, Long> ids = new LinkedHashMap<>();
    Map<String, String> tokens = new HashMap<>();
    List<EvidenceCase> evidence = new ArrayList<>();
    OffsetDateTime started;
    Long book, copy, loan, item, reservation, fee;

    @Test void fourRoleLoginDeniedMutationsOwnershipFeeSuccessAndProtectedReportPersistence() throws Exception {
        started = OffsetDateTime.now();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String password = "S4091-Test-Only!";
        String encoded = encoder.encode(password);
        for (String role : List.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER", "OTHER_READER")) {
            String email = "s4091-" + UUID.randomUUID() + "@example.invalid";
            Long id = jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,?,?,?,'ACTIVE' FROM roles WHERE code=? RETURNING id",
                    Long.class, "S4091 " + role, email, encoded, role.equals("OTHER_READER") ? "READER" : role);
            ids.put(role, id);
            var login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
            assertEquals(200, login.getResponse().getStatus(), "Bốn vai trò phải đăng nhập được bằng mật khẩu thử.");
            tokens.put(role, mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText());
        }
        fixture();
        try {
            String direct = mapper.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "cardNumber", "S4091-CARD", "barcodes", List.of("S4091-BOOK")));
            String returned = mapper.writeValueAsString(Map.of("barcode", "S4091-BOOK", "itemId", item));
            String repair = "{\"reason\":\"Không được phép thay đổi kho\"}";
            probe("ReaderController.getReaderById", "READER", "STANDARD", "GET", "/api/v1/readers/" + ids.get("OTHER_READER"), null, 403);
            for (String role : List.of("READER", "ANONYMOUS", "LIBRARY_MANAGER")) {
                probe("LoanController.createDirectLoan", role, "STANDARD", "POST", "/api/v1/loans/direct", direct, role.equals("ANONYMOUS") ? 401 : 403);
            }
            probe("LoanController.confirmReturn", "READER", "STANDARD", "POST", "/api/v1/loans/return-confirmation", returned, 403);
            probe("BookCopyController.repair", "READER", "STANDARD", "POST", "/api/v1/book-copies/" + copy + "/repair", repair, 403);
            for (String role : List.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN")) {
                probe("BookReservationController.reserve", role, "STANDARD", "POST", "/api/v1/books/" + book + "/reservations", null, 403);
            }
            probe("LoanController.loanDetail", "READER", "STANDARD", "GET", "/api/v1/loans/" + loan, null, 200);
            var foreign = mvc.perform(MockMvcRequestBuilders.get("/api/v1/loans/" + loan)
                    .param("readerId", ids.get("READER").toString()).header("Authorization", "Bearer " + tokens.get("OTHER_READER"))).andReturn();
            assertEquals(404, foreign.getResponse().getStatus());
            assertFalse(foreign.getResponse().getContentAsString().contains("borrower_user_id"));
            evidence.add(new EvidenceCase("LoanController.loanDetail", "READER", "OTHER_OWNER", 404, null, 404, true,
                    started, OffsetDateTime.now(), "Hai tài khoản Bạn đọc thật: đổi ID phiếu và giả readerId vẫn HTTP 404, không trả chi tiết phiếu của người khác.", null));
            var preview = probe("FeePaymentController.detail", "LIBRARIAN", "STANDARD", "GET",
                    "/api/v1/fee-payments/readers/" + ids.get("READER"), null, 200);
            var debt = mapper.readTree(preview);
            String payment = mapper.writeValueAsString(Map.of("requestKey", UUID.randomUUID(), "expectedTotalVnd", 10000,
                    "debtToken", debt.get("debtToken").asText()));
            for (String role : List.of("ADMIN", "LIBRARY_MANAGER", "READER", "ANONYMOUS")) {
                probe("FeePaymentController.collect", role, "STANDARD", "POST",
                        "/api/v1/fee-payments/readers/" + ids.get("READER") + "/collect-full", payment, role.equals("ANONYMOUS") ? 401 : 403);
            }
            probe("FeePaymentController.collect", "LIBRARIAN", "STANDARD", "POST",
                    "/api/v1/fee-payments/readers/" + ids.get("READER") + "/collect-full", payment, 200);
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM fee_payments WHERE reader_user_id=?", Long.class, ids.get("READER")));
            assertEquals(10000L, jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?", Long.class, fee));

            ImportReport report = report();
            String json = mapper.writeValueAsString(report);
            var saved = mvc.perform(MockMvcRequestBuilders.post("/api/v1/permission-review/reports")
                    .header("Authorization", "Bearer " + tokens.get("ADMIN")).contentType("application/json").content(json)).andReturn();
            assertEquals(201, saved.getResponse().getStatus(), saved.getResponse().getContentAsString());
            Long reportId = mapper.readTree(saved.getResponse().getContentAsString()).get("summary").get("id").asLong();
            assertEquals(ids.get("ADMIN"), jdbc.queryForObject("SELECT imported_by_user_id FROM permission_review_runs WHERE id=?", Long.class, reportId));
            for (String role : List.of("READER", "LIBRARIAN", "LIBRARY_MANAGER")) {
                assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/permission-review/reports/" + reportId)
                        .header("Authorization", "Bearer " + tokens.get(role))).andReturn().getResponse().getStatus());
            }
            assertEquals(409, mvc.perform(MockMvcRequestBuilders.post("/api/v1/permission-review/reports")
                    .header("Authorization", "Bearer " + tokens.get("ADMIN")).contentType("application/json").content(json))
                    .andReturn().getResponse().getStatus());
        } finally {
            Path output = Path.of("target/permission-review/postgres.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report()));
        }
    }
    ImportReport report() {
        return new ImportReport(1, UUID.randomUUID(), catalog.policy().version(), inspector.inspect().fingerprint(),
                "REGRESSION", "ISOLATED_POSTGRES", "CriticalPermissionDatabaseTest / disposable PostgreSQL / rollback",
                System.getProperty("permission.review.executedBy", System.getProperty("user.name")), started, OffsetDateTime.now(), List.copyOf(evidence));
    }
    String probe(String endpoint, String role, String scenario, String method, String path, String body, int expected) throws Exception {
        String before = snapshot(); OffsetDateTime at = OffsetDateTime.now();
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
        if (!role.equals("ANONYMOUS")) request.header("Authorization", "Bearer " + tokens.get(role));
        if (body != null) request.contentType("application/json").content(body);
        var result = mvc.perform(request).andReturn(); int actual = result.getResponse().getStatus();
        boolean denied = expected == 401 || expected == 403 || expected == 404;
        Boolean unchanged = denied ? before.equals(snapshot()) : null;
        evidence.add(new EvidenceCase(endpoint, role, scenario, expected, null, actual, unchanged, at, OffsetDateTime.now(),
                "PostgreSQL thật và JWT ký thật. Đối chiếu snapshot phiếu mượn, chi tiết trả, bản sao, đặt giữ, khoản nợ và giao dịch thu phí; dữ liệu thử rollback sau test.", null));
        assertEquals(expected, actual, result.getResponse().getContentAsString());
        if (denied) assertEquals(Boolean.TRUE, unchanged, "Yêu cầu bị từ chối đã thay đổi dữ liệu!");
        return result.getResponse().getContentAsString();
    }
    String snapshot() {
        List<Object> data = new ArrayList<>();
        for (var target : Map.of("loans", loan, "loan_items", item, "book_copies", copy, "book_reservations", reservation, "reader_fees", fee).entrySet()) {
            data.add(jdbc.queryForList("SELECT to_jsonb(t)::text AS row FROM " + target.getKey() + " t WHERE id=?", target.getValue()));
        }
        data.add(jdbc.queryForList("SELECT to_jsonb(t)::text AS row FROM fee_payments t WHERE reader_user_id=? ORDER BY id", ids.get("READER")));
        return mapper.writeValueAsString(data);
    }
    void fixture() {
        String suffix = UUID.randomUUID().toString();
        Long warehouse = jdbc.queryForObject("INSERT INTO warehouses(code,name) VALUES (?,?) RETURNING id", Long.class, "S4091-" + suffix, "Kho S4091 " + suffix);
        Long shelf = jdbc.queryForObject("INSERT INTO shelves(warehouse_id,code,name) VALUES (?,?,?) RETURNING id", Long.class, warehouse, "S4091", "Kệ thử");
        book = jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT min(id) FROM authors),(SELECT min(id) FROM categories)) RETURNING id", Long.class, "S4091 " + suffix);
        copy = jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,?,'AVAILABLE',CURRENT_DATE-1,50000,'GOOD') RETURNING id", Long.class, book, "S4091-" + suffix, shelf);
        loan = jdbc.queryForObject("INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES (?,?,?) RETURNING id", Long.class, "S4091-" + suffix, ids.get("READER"), ids.get("LIBRARIAN"));
        item = jdbc.queryForObject("INSERT INTO loan_items(loan_id,book_copy_id,due_date) VALUES (?,?,CURRENT_TIMESTAMP+interval '14 days') RETURNING id", Long.class, loan, copy);
        reservation = jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id) VALUES (?,?) RETURNING id", Long.class, book, ids.get("READER"));
        fee = jdbc.queryForObject("INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES (?,'S4091 phí thử',10000) RETURNING id", Long.class, ids.get("READER"));
    }
}
