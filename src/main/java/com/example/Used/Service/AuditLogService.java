package com.example.Used.Service;

import com.example.Used.Model.AuditLog;
import com.example.Used.Model.User;
import com.example.Used.Repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditRepository auditRepository;
    private final NotificationService notificationService;


    @Autowired
    public AuditLogService(
            AuditRepository auditRepository,
            NotificationService notificationService
    ) {
        this.auditRepository = auditRepository;
        this.notificationService = notificationService;
    }

    public void log(AuditLog.AuditAction action, User actor, String details) {
        log(action, actor, details, null);
    }

    public void log(AuditLog.AuditAction action, User actor, String details, Long notifyUserId) {

        // 1) Console
        String actorName = (actor != null) ? actor.getUsername() : "SYSTEM";
        logger.info("[AUDIT] {} | actor={} | {}", action, actorName, details);

        // 2) Persist
        AuditLog entry = new AuditLog(action, actor, details);
        AuditLog saved = auditRepository.save(entry);

        // 3) Live notification, routed per the matrix
        if (notifyUserId == null) {
            notificationService.broadcast(action.name(), saved);
        } else {
            notificationService.sendToUser(notifyUserId, action.name(), saved);
        }
    }

    public List<AuditLog> getUserActivity(Long userId) {
        return auditRepository.findByActorIdOrderByCreatedAtDesc(userId);
    }
}
