package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.OverdueDebtReportRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

/** Opt-in PostgreSQL test using session-local temporary tables; never writes production rows. */
@EnabledIfEnvironmentVariable(named="S4_08_1_DB_TEST",matches="true")
class OverdueDebtReportDatabaseTest {
    private Connection connection;
    private JdbcTemplate jdbc;
    private OverdueDebtReportRepository repository;
    private final OffsetDateTime today=OffsetDateTime.parse("2026-10-15T00:00:00+07:00");
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("S4_08_1_TEST_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url!=null && url.matches("jdbc:postgresql://.*/[^?]*test[^?]*(\\?.*)?"),"Chỉ dùng database riêng có tên chứa test.");
        connection=DriverManager.getConnection(url,System.getenv("S4_08_1_TEST_USER"),System.getenv("S4_08_1_TEST_PASSWORD"));
        connection.setAutoCommit(false);jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
        // PostgreSQL searches pg_temp before public: the actual repository SQL runs against these fixtures only.
        jdbc.execute("CREATE TEMP TABLE users(id BIGINT PRIMARY KEY, full_name TEXT, phone TEXT)");
        jdbc.execute("CREATE TEMP TABLE library_cards(user_id BIGINT UNIQUE,card_number TEXT,card_type_id BIGINT)");
        jdbc.execute("CREATE TEMP TABLE loans(id BIGINT PRIMARY KEY,borrower_user_id BIGINT)");
        jdbc.execute("CREATE TEMP TABLE loan_items(id BIGINT,loan_id BIGINT,due_date TIMESTAMPTZ,returned_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TEMP TABLE reader_fees(id BIGINT,reader_user_id BIGINT,amount_vnd BIGINT,paid_amount_vnd BIGINT,waived_amount_vnd BIGINT)");
        jdbc.execute("CREATE TEMP TABLE book_incidents(id BIGINT,compensation_fee_id BIGINT)");
        jdbc.execute("CREATE TEMP TABLE book_incident_adjustments(incident_id BIGINT,status TEXT)");
        jdbc.update("INSERT INTO users VALUES (1,'Nguyễn Văn A','0901'),(2,'Chỉ nợ','0902'),(3,'Chỉ quá hạn','0903'),(4,'Bình thường','0904')");
        jdbc.update("INSERT INTO library_cards VALUES (1,'001',1),(2,'002',1),(3,'003',2),(4,'004',2)");
        jdbc.update("INSERT INTO loans VALUES (11,1),(12,1),(13,3),(14,4)");
        jdbc.update("""
            INSERT INTO loan_items VALUES
            (101,11,'2026-10-01T18:00:00+07:00',NULL),
            (102,11,'2026-10-14T18:00:00+07:00',NULL),
            (103,12,'2026-10-02T18:00:00+07:00',NULL),
            (104,12,'2026-10-01T18:00:00+07:00','2026-10-10T10:00:00+07:00'),
            (105,13,'2026-10-14T18:00:00+07:00',NULL),
            (106,14,'2026-10-15T00:00:00+07:00',NULL),
            (107,14,NULL,NULL)
            """);
        jdbc.update("INSERT INTO reader_fees VALUES (1,1,100000,20000,5000),(2,1,30000,10000,0),(3,1,40000,40000,0),(4,1,200000,0,0),(5,2,30000,0,0)");
        jdbc.update("INSERT INTO book_incidents VALUES (1,4)");
        jdbc.update("INSERT INTO book_incident_adjustments VALUES (1,'CONFIRMED')");
        repository=new OverdueDebtReportRepository(jdbc);
    }
    @AfterEach void close() throws Exception { if(connection!=null){connection.rollback();connection.close();} }
    @Test void unionDistinctReadersPartialReturnsMultipleLoansAndNetFees() {
        var readers=repository.readers(today);
        assertThat(readers).extracting(OverdueDebtReportRepository.ReaderFacts::readerId).containsExactly(1L,2L,3L);
        assertThat(readers.get(0).cardNumber()).isEqualTo("001");assertThat(readers.get(0).phone()).isEqualTo("0901");
        assertThat(readers.get(0).debt()).isEqualByComparingTo("95000"); // 75k + 20k; settled and reversed compensation excluded
        var items=repository.overdueItems(today);
        assertThat(items.stream().filter(i->i.readerId()==1L).count()).isEqualTo(3);
        assertThat(items.stream().filter(i->i.readerId()==3L).count()).isEqualTo(1);
        assertThat(items).noneMatch(i->i.readerId()==4L);
    }
    @Test void noOverdueAndNoDebtMeansNoData() {
        jdbc.update("UPDATE loan_items SET returned_at=?",today);jdbc.update("UPDATE reader_fees SET paid_amount_vnd=amount_vnd-waived_amount_vnd");
        assertThat(repository.readers(today)).isEmpty();assertThat(repository.overdueItems(today)).isEmpty();
    }
}
