package com.duanttcsn5.library.config;

import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Configuration
public class BootstrapAdminConfig {

    @Bean
    ApplicationRunner bootstrapAdmin(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email:admin@libra.edu.vn}") String configuredEmail,
            @Value("${app.bootstrap-admin.password:}") String configuredPassword,
            @Value("${app.bootstrap-admin.full-name:Quản trị hệ thống}") String configuredFullName) {

        return args -> {
            if (configuredPassword == null || configuredPassword.isBlank()) {
                return;
            }

            String email = configuredEmail.trim().toLowerCase(Locale.ROOT);
            if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
                return;
            }

            Role adminRole = roleRepository.findByCode("ADMIN")
                    .orElseThrow(() -> new IllegalStateException(
                            "Không tìm thấy role ADMIN. Hãy kiểm tra Flyway migration V1."));

            User user = new User();
            user.setRole(adminRole);
            user.setFullName(configuredFullName);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(configuredPassword));
            user.setStatus("ACTIVE");
            user.setFailedLoginAttempts(0);
            user.setTokenVersion(0);

            userRepository.save(user);
        };
    }
}
