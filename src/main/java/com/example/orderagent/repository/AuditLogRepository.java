package com.example.orderagent.repository;

import com.example.orderagent.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the {@code audit_log} table. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTraceIdOrderByIdAsc(String traceId);
}
