package com.duanttcsn5.library;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LibraryManagementBackendApplication {

    public static void main(String[] args) {
        // Force a canonical IANA timezone before PostgreSQL/JDBC is initialized.
        // Some Windows/JDK combinations resolve the local timezone as "Asia/Saigon",
        // which PostgreSQL 15 in Docker may reject during the startup handshake.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        SpringApplication.run(LibraryManagementBackendApplication.class, args);
    }
}
