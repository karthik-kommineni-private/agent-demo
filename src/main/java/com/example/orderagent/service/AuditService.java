package com.example.orderagent.service;

import com.example.orderagent.entity.AuditLog;
import com.example.orderagent.repository.AuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Writes audit rows. The only thing between {@code AuditInterceptor} and
 * the database — kept this thin deliberately, since redaction (the part
 * that actually matters for what ends up in this table) already happened
 * before this class is called.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String traceId, String toolName, String argumentsJson, String resultJson, boolean success) {
        auditLogRepository.save(new AuditLog(traceId, toolName, argumentsJson, resultJson, success, Instant.now()));
    }
}
