package com.example.audit.service;

import com.example.audit.model.AuditLog;
import com.example.audit.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditRepository auditRepository;

    public AuditLogService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(AuditLog.AuditAction action, Long actorId, String actorUsername, String details) {
        log(action, actorId, actorUsername, details, null);
    }

    public void log(AuditLog.AuditAction action, Long actorId, String actorUsername,
                    String details, Long notifyUserId) {

        // 1) Console
        String actorName = (actorId != null) ? actorUsername : "SYSTEM";
        logger.info("[AUDIT] {} | actor={} | {}", action, actorName, details);

        // 2) Persist
        AuditLog entry = new AuditLog(action, actorId, actorUsername, details);
        auditRepository.save(entry);

        // 3) Live notification push is deferred to the async phase (RabbitMQ).
        //    In the sync phase Audit only persists; consumers react to events later.
        //    notifyUserId is retained for API compatibility.
    }

    public List<AuditLog> getUserActivity(Long userId) {
        return auditRepository.findByActorIdOrderByCreatedAtDesc(userId);
    }
}
