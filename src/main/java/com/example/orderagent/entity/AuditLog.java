package com.example.orderagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per tool call, written after every execution regardless of
 * outcome. This is what lets a trace ID recorded in an {@code AgentResponse}
 * be cross-referenced against what the system actually did.
 *
 * <p>{@code argumentsJson} and {@code resultJson} are redacted before they
 * reach this entity — see {@code RedactionInterceptor} — so this table
 * never becomes the place PII leaks out through, per non-negotiable rule 9.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "arguments_json", length = 2000)
    private String argumentsJson;

    @Column(name = "result_json", length = 2000)
    private String resultJson;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
        // required by JPA
    }

    public AuditLog(String traceId, String toolName, String argumentsJson, String resultJson, boolean success, Instant createdAt) {
        this.traceId = traceId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.resultJson = resultJson;
        this.success = success;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
