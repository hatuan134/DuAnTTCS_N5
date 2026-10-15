package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** Versioned PO-approved document; it does not grant runtime permissions. */
@Component
public class PermissionReviewCatalog {
    private final Policy policy;
    private final byte[] source;
    private final Map<String, PolicyEntry> entries;

    public PermissionReviewCatalog(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("permissions/S4-09.1-policy.json").getInputStream()) {
            source = stream.readAllBytes();
        }
        policy = mapper.readValue(source, Policy.class);
        Map<String, PolicyEntry> indexed = new LinkedHashMap<>();
        Set<String> roleCodes = Set.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER");
        Set<String> modes = Set.of("ALLOW", "DENY", "OWN", "OVERRIDE");
        Set<String> moduleCodes = new HashSet<>();
        policy.modules().forEach(module -> moduleCodes.add(module.code()));
        for (PolicyEntry entry : policy.entries()) {
            if (indexed.put(entry.id(), entry) != null || !entry.rules().keySet().equals(roleCodes)
                    || !modes.containsAll(entry.rules().values()) || !moduleCodes.contains(entry.module())
                    || entry.httpMethods().isEmpty() || !entry.path().startsWith("/api/v1/")) {
                throw new IllegalStateException("Bảng quyền S4-09.1 không hợp lệ: " + entry.id());
            }
        }
        entries = Collections.unmodifiableMap(indexed);
    }

    public Policy policy() { return policy; }
    public byte[] source() { return source.clone(); }
    public Optional<PolicyEntry> find(String id) { return Optional.ofNullable(entries.get(id)); }

    public int requiredStatus(PolicyEntry entry, String role, String scenario, int successStatus) {
        if ("ANONYMOUS".equals(role)) return 401;
        String mode = entry.rules().getOrDefault(role, "DENY");
        if ("DENY".equals(mode)) return 403;
        if ("OTHER_OWNER".equals(scenario)) {
            if (!"OWN".equals(mode)) throw new IllegalArgumentException("Không phải luồng sở hữu tài nguyên.");
            return 404;
        }
        if ("OVERRIDE".equals(mode) && !"OVERRIDE".equals(scenario)) return 403;
        if (successStatus < 200 || successStatus >= 300) {
            throw new IllegalArgumentException("Luồng được phép phải có kết quả nghiệp vụ 2xx.");
        }
        return successStatus;
    }
}
