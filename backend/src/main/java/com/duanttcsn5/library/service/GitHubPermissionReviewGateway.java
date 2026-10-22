package com.duanttcsn5.library.service;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Only dispatches the fixed, repository-owned workflow on main; never accepts arbitrary commands. */
@Component
public class GitHubPermissionReviewGateway {
    private final String owner, repository, token, callbackSecret;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GitHubPermissionReviewGateway(
            @Value("${app.permission-review.github.owner:}") String owner,
            @Value("${app.permission-review.github.repository:}") String repository,
            @Value("${app.permission-review.github.token:}") String token,
            @Value("${app.permission-review.github.callback-secret:}") String callbackSecret) {
        this.owner = owner;
        this.repository = repository;
        this.token = token;
        this.callbackSecret = callbackSecret;
    }

    public void requireConfigured() {
        if (!owner.matches("[A-Za-z0-9-]{1,39}") || !repository.matches("[A-Za-z0-9_.-]{1,100}")
                || token.isBlank() || callbackSecret.length() < 32)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PERMISSION_GITHUB_NOT_CONFIGURED",
                    "Chưa cấu hình GitHub Actions hoặc khóa xác thực callback trên Backend.");
    }

    public String callbackSecret() { return callbackSecret; }

    public boolean acceptsRepository(String value) {
        return !owner.isBlank() && !repository.isBlank() && (owner + "/" + repository).equals(value);
    }

    public void dispatch(UUID key, String fingerprint) throws Exception {
        requireConfigured();
        if (!fingerprint.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid fingerprint");
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repository
                + "/actions/workflows/permission-review.yml/dispatches");
        String body = "{\"ref\":\"main\",\"inputs\":{\"run_key\":\"" + key
                + "\",\"fingerprint\":\"" + fingerprint + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 204)
            throw new IllegalStateException("GitHub workflow dispatch returned HTTP " + response.statusCode());
    }
}
