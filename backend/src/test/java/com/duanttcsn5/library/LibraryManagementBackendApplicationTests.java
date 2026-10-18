package com.duanttcsn5.library;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-independent Spring Boot wiring smoke test.
 *
 * The normal test application.yml defaults PostgreSQL to 127.0.0.1:1234.
 * A developer running the unit suite without a dedicated test database should
 * not accidentally run Flyway against a real or production PostgreSQL database.
 * Database/Flyway integration must be exercised separately against an isolated
 * test database; this test does NOT claim to validate migrations or DB access.
 */
@SpringBootTest(classes = LibraryManagementBackendApplication.class, properties = {
        "spring.main.lazy-initialization=true",
        "spring.flyway.enabled=false",
        // The application-ready listener would otherwise query the production-style repository.
        "app.permission-review.launch-enabled=false"
})
class LibraryManagementBackendApplicationTests {

    @Autowired ApplicationContext context;

    @Test
    void contextLoads() {
        // Verify the application's configuration has been discovered without
        // instantiating database-dependent services or connecting to PostgreSQL.
        assertTrue(context.containsBeanDefinition("aiChatService"));
        assertTrue(context.containsBeanDefinition("aiProviderRouter"));
    }
}
