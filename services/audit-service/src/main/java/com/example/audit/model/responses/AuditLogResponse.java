package com.example.audit.model.responses;

import com.example.audit.model.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(Long id, String action, Long actorId, String actorUsername,
                               String details, LocalDateTime createdAt) {

    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getAction() == null ? null : entry.getAction().name(),
                entry.getActorId(),
                entry.getActorUsername(),
                entry.getDetails(),
                entry.getCreatedAt());
    }
}
