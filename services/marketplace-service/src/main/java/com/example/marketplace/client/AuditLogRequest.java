package com.example.marketplace.client;

import jakarta.validation.constraints.NotBlank;

public record AuditLogRequest(
        @NotBlank String action,
        Long actorId,
        String actorUsername,
        @NotBlank String details,
        Long notifyUserId) {
}
