package com.example.accounts.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLogEntry(Long id, String action, Long actorId, String actorUsername,
                            String details, LocalDateTime createdAt) {
}
