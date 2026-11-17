package com.example.accounts.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AuditClient {

    private final RestClient http;

    public AuditClient(RestClient auditRestClient) {
        this.http = auditRestClient;
    }

    public void log(String action, Long actorId, String actorUsername, String details) {
        log(action, actorId, actorUsername, details, null);
    }

    public void log(String action, Long actorId, String actorUsername, String details, Long notifyUserId) {
        http.post()
                .uri("/api/internal/audit/log")
                .body(new AuditLogRequest(action, actorId, actorUsername, details, notifyUserId))
                .retrieve()
                .toBodilessEntity();
    }

    public List<AuditLogEntry> getUserActivity(Long userId) {
        return http.get()
                .uri("/api/internal/audit/activity/{id}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<AuditLogEntry>>() {});
    }
}
