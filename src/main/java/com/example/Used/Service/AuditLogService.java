package com.example.Used.Service;

import com.example.Used.Model.AuditLog;
import com.example.Used.Model.User;
import com.example.Used.Repository.AuditRepository;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditRepository auditRepository;

    @Autowired
    public AuditLogService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(AuditLog.AuditAction action, User actor, String details) {

        // 1) Console log (uses {} placeholders, not string concat -> fast + clean)
        String actorName = (actor != null) ? actor.getUsername() : "SYSTEM";
        logger.info("[AUDIT] {} | actor={} | {}", action, actorName, details);

        // 2) Persist to database
        AuditLog entry = new AuditLog(action, actor, details);
        auditRepository.save(entry);
    }
}
