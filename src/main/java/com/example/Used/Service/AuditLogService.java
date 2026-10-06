package com.example.Used.Service;

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

}
