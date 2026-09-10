package com.example.orderagent.dto.tool;

import java.math.BigDecimal;

/**
 * Result of {@code issueRefund}.
 *
 * @param replay true if this call replayed an idempotency key seen before
 *               — {@code amountRefunded} was not applied again
 */
public record IssueRefundOutput(Long orderId, BigDecimal amountRefunded, BigDecimal totalRefunded, boolean replay) {
}
