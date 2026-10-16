package com.duanttcsn5.library.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String message = "POST".equals(request.getMethod())
                && request.getRequestURI().substring(request.getContextPath().length()).matches("/api/v1/books/[^/]+/reservations")
                ? "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập để đặt giữ đầu sách."
                : "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.";

        String json = """
                {
                  "message": "%s",
                  "code": "UNAUTHORIZED",
                  "timestamp": "%s"
                }
                """.formatted(message, OffsetDateTime.now());

        response.getWriter().write(json);
        response.getWriter().flush();
    }
}