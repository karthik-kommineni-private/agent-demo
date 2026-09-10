package com.example.orderagent.dto.tool;

import java.math.BigDecimal;

/**
 * Arguments for {@code issueRefund}.
 *
 * <p>{@code idempotencyKey} must be supplied by the caller (the model) and
 * is what makes a replayed call safe — see {@code IssueRefundTool}.
 * {@code reason} isn't used by the tool's own logic; it exists for the
 * audit row {@code AuditInterceptor} writes in Phase 4.
 */
public record IssueRefundInput(Long orderId, BigDecimal amount, String reason, String idempotencyKey) {
}
