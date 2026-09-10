package com.example.orderagent.service.tool;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.dto.tool.IssueRefundInput;
import com.example.orderagent.dto.tool.IssueRefundOutput;
import com.example.orderagent.exception.ToolExecutionException;
import com.example.orderagent.service.OrderService;
import com.example.orderagent.util.IdempotencyKeys;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Issues a refund against an order.
 *
 * <p>This is the one write tool in the project, which is why it carries
 * the most surrounding machinery — from Phase 4 on, every call to it also
 * passes through an allowlist check, a policy cap check (refund ≤ order
 * total, not already refunded, order exists) and breaker registration.
 * None of those policy checks live in this class; see
 * {@code service.governance.PolicyInterceptor}. This class only knows how
 * to apply a refund, and how not to apply the same one twice.
 *
 * <p><b>How it fails:</b> an invalid idempotency key, a non-positive
 * amount, or no such order all surface as {@link ToolExecutionException}.
 * Whether the refund <i>should</i> happen at all is decided upstream,
 * before this class is ever reached.
 */
@Component
public class IssueRefundTool implements OrderAgentTool<IssueRefundInput, IssueRefundOutput> {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "orderId": {
                  "type": "integer",
                  "description": "The numeric order id to refund."
                },
                "amount": {
                  "type": "number",
                  "exclusiveMinimum": 0,
                  "description": "The amount to refund, in dollars."
                },
                "reason": {
                  "type": "string",
                  "description": "Why the refund is being issued, recorded for the audit trail."
                },
                "idempotencyKey": {
                  "type": "string",
                  "description": "A unique key for this refund attempt. Replaying the same key returns the original result without refunding again."
                }
              },
              "required": ["orderId", "amount", "reason", "idempotencyKey"],
              "additionalProperties": false
            }
            """;

    private final OrderService orderService;

    // In-memory only: one process, one lifetime. A restart forgets every
    // key, which is fine for this demo's single-instance H2 database but
    // would not be safe run across multiple instances — see
    // docs/production-readiness.md.
    private final Map<String, IssueRefundOutput> seenIdempotencyKeys = new ConcurrentHashMap<>();

    public IssueRefundTool(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String name() {
        return "issueRefund";
    }

    @Override
    public String description() {
        return "Issues a refund against an order. Replaying the same idempotencyKey does not refund twice.";
    }

    @Override
    public String inputSchema() {
        return INPUT_SCHEMA;
    }

    @Override
    public Class<IssueRefundInput> inputType() {
        return IssueRefundInput.class;
    }

    @Override
    public IssueRefundOutput execute(IssueRefundInput input) {
        if (!IdempotencyKeys.isValid(input.idempotencyKey())) {
            throw new ToolExecutionException(name(), "Invalid idempotency key");
        }
        if (input.amount() == null || input.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ToolExecutionException(name(), "Refund amount must be greater than zero");
        }

        IssueRefundOutput previous = seenIdempotencyKeys.get(input.idempotencyKey());
        if (previous != null) {
            // Same key seen before: hand back the original result instead of
            // touching the order again. This is what makes a model retry (or
            // a network-level retry) safe to replay.
            return new IssueRefundOutput(
                    previous.orderId(), previous.amountRefunded(), previous.totalRefunded(), true);
        }

        OrderDto updated = orderService
                .applyRefund(input.orderId(), input.amount())
                .orElseThrow(() -> new ToolExecutionException(name(), "No order found with id " + input.orderId()));

        IssueRefundOutput result = new IssueRefundOutput(updated.id(), input.amount(), updated.refundedAmount(), false);
        seenIdempotencyKeys.put(input.idempotencyKey(), result);
        return result;
    }
}
