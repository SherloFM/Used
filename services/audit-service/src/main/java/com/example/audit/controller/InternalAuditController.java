package com.example.audit.controller;

import com.example.audit.model.AuditLog;
import com.example.audit.model.requests.AuditLogRequest;
import com.example.audit.model.responses.AuditLogResponse;
import com.example.audit.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/internal/audit")
public class InternalAuditController {

    private final AuditLogService auditLogService;

    public InternalAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping("/log")
    public ResponseEntity<Void> log(@Valid @RequestBody AuditLogRequest request) {
        auditLogService.log(
                AuditLog.AuditAction.valueOf(request.action()),
                request.actorId(),
                request.actorUsername(),
                request.details(),
                request.notifyUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activity/{userId}")
    public List<AuditLogResponse> activity(@PathVariable Long userId) {
        return auditLogService.getUserActivity(userId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
