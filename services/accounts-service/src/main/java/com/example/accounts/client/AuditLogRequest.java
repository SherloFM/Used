package com.example.accounts.client;

import jakarta.validation.constraints.NotBlank;

public record AuditLogRequest(
        @NotBlank String action,
        Long actorId,
        String actorUsername,
        @NotBlank String details,
        Long notifyUserId) {
}
