package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PermissionReviewGitHubCallbackSignatureTest {
    private static final String SECRET = "local-integration-test-secret-never-use-in-production";
    private final PermissionReviewGitHubCallbackService service = new PermissionReviewGitHubCallbackService(
            new GitHubPermissionReviewGateway("test-owner", "test-repo", "fake-only", SECRET),
            null, null, null, null);

    private String signature(String timestamp, String body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(hmac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void signedBodyIsAcceptedButMutatedBodyIsRejected() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String body = "{\"runKey\":null}";
        assertDoesNotThrow(() -> service.verify(timestamp, signature(timestamp, body), body));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.verify(timestamp, signature(timestamp, body), "{\"runKey\":123}"));
        assertEquals(401, ex.getStatus().value());
    }

    @Test void staleSignatureAndMissingSignatureAreRejected() throws Exception {
        String timestamp = Long.toString(Instant.now().minusSeconds(3600).getEpochSecond());
        assertEquals(401, assertThrows(ApiException.class,
                () -> service.verify(timestamp, signature(timestamp, "{}"), "{}")).getStatus().value());
        assertEquals(401, assertThrows(ApiException.class,
                () -> service.verify(timestamp, null, "{}")).getStatus().value());
    }
}
