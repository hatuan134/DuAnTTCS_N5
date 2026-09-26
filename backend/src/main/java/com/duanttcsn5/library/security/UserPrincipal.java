package com.duanttcsn5.library.security;

public record UserPrincipal(
        Long id,
        String email,
        String fullName,
        String role,
        int tokenVersion
) {
}
