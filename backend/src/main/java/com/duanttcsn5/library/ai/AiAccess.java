package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.security.UserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AiAccess {
    public UserPrincipal principal() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && a.getPrincipal() instanceof UserPrincipal p ? p : null;
    }
    public String role() { var p = principal(); return p == null ? "PUBLIC" : p.role(); }
    public void require(String... roles) {
        var a = SecurityContextHolder.getContext().getAuthentication();
        var p = principal();
        if (p != null && a != null) for (String role : roles)
            if (role.equals(p.role()) && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + role))) return;
        throw new AccessDeniedException("Bạn không có quyền sử dụng công cụ này.");
    }
    public Long readerId() { require("READER"); return principal().id(); }
    public Long staffId() { require("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"); return principal().id(); }
    public String owner(String remoteAddress) {
        var p = principal();
        return p == null ? "guest:" + remoteAddress : "user:" + p.id() + ":" + p.tokenVersion() + ":" + p.role();
    }
}
