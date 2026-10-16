package com.duanttcsn5.library;

import com.duanttcsn5.library.service.PermissionReviewInspector;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;

class PermissionReviewInspectorTest {
    static class Excess {
        static class FeePaymentController {
            @PreAuthorize("hasAnyRole('LIBRARIAN','READER')")
            public void collect() {}
        }
    }
    static class Missing {
        static class FeePaymentController {
            @PreAuthorize("hasRole('ADMIN')")
            public void collect() {}
        }
    }
    void detects(Object replacement, String role, String observed) throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(CriticalPermissionApiTest.Config.class); context.refresh();
            var inspector = context.getBean(PermissionReviewInspector.class);
            String oldFingerprint = inspector.inspect().fingerprint();
            var mappings = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            var original = mappings.getHandlerMethods().keySet().stream()
                    .filter(value -> value.getPatternValues().contains("/api/v1/fee-payments/readers/{readerId}/collect-full"))
                    .findFirst().orElseThrow();
            mappings.unregisterMapping(original);
            mappings.registerMapping(original, replacement, replacement.getClass().getMethod("collect"));
            var snapshot = inspector.inspect();
            var row = snapshot.entries().stream().filter(value -> value.policy().id().equals("FeePaymentController.collect")).findFirst().orElseThrow();
            assertTrue(row.mapped()); assertTrue(row.different());
            var comparison = row.roles().stream().filter(value -> value.role().equals(role)).findFirst().orElseThrow();
            assertEquals(observed, comparison.actualGate()); assertFalse(comparison.matches());
            assertNotEquals(oldFingerprint, snapshot.fingerprint());
        }
    }
    @Test void detectsAnExcessReaderPermission() throws Exception { detects(new Excess.FeePaymentController(), "READER", "ALLOW"); }
    @Test void detectsAMissingLibrarianPermission() throws Exception { detects(new Missing.FeePaymentController(), "LIBRARIAN", "DENY"); }
    static class UndocumentedApi { public void probe() {} }
    @Test void discoversAnUndocumentedRuntimeApiAndMarksEveryRoleUnknown() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            var servlet=new MockServletContext();context.setServletContext(servlet);context.register(CriticalPermissionApiTest.Config.class);context.refresh();
            var mappings=context.getBean("requestMappingHandlerMapping",RequestMappingHandlerMapping.class);
            var mapping=org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths("/api/v1/s4092-undocumented")
                    .methods(org.springframework.web.bind.annotation.RequestMethod.GET).build();
            mappings.registerMapping(mapping,new UndocumentedApi(),UndocumentedApi.class.getMethod("probe"));
            var inspector=new PermissionReviewInspector(new com.duanttcsn5.library.service.PermissionReviewCatalog(new tools.jackson.databind.ObjectMapper()),mappings,
                    context.getBean(org.springframework.security.web.SecurityFilterChain.class),servlet);
            var result=inspector.inspect();
            assertTrue(result.undocumentedApis().contains("GET /api/v1/s4092-undocumented"));
            var row=result.entries().stream().filter(e->e.policy().path().equals("/api/v1/s4092-undocumented")).findFirst().orElseThrow();
            assertTrue(row.different());assertEquals(4,row.roles().size());
            assertTrue(row.roles().stream().allMatch(r->r.documented().equals("UNKNOWN") && !r.matches()));
            assertEquals(192,result.totalApis());assertTrue(result.unresolvedFunctions()>0);
        }
    }

}
