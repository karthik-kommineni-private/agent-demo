package com.example.orderagent.service.governance;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.dto.tool.IssueRefundInput;
import com.example.orderagent.exception.PolicyViolationException;
import com.example.orderagent.service.OrderService;
import java.math.BigDecimal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The refund policy, enforced in code. This is the guardrail the project
 * exists to demonstrate: no matter what the model decides, or what text a
 * customer or an order's notes contain, a refund cannot exceed what's
 * actually left to refund.
 *
 * <p>Only acts on {@code issueRefund} — a read-only lookup has nothing for
 * a refund policy to say about it, so this class does nothing for any
 * other tool. Runs after {@link AllowlistInterceptor} ({@code @Order(20)}),
 * so a call already rejected by the allowlist never reaches these checks.
 *
 * <p><b>How it fails:</b> every check here either passes silently or
 * throws {@link PolicyViolationException} with a reason specific enough
 * for the model to explain to the customer. There is no other failure
 * mode — this class never touches the database beyond a single read.
 */
@Component
@Order(20)
public class PolicyInterceptor implements ToolInterceptor {

    private final OrderService orderService;

    public PolicyInterceptor(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void preInvoke(ToolCallContext ctx) {
        if (!(ctx.input() instanceof IssueRefundInput refundRequest)) {
            return;
        }

        OrderDto order = orderService
                .findById(refundRequest.orderId())
                .orElseThrow(() -> new PolicyViolationException(
                        "Order " + refundRequest.orderId() + " does not exist."));

        BigDecimal remaining = order.total().subtract(order.refundedAmount());

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PolicyViolationException(
                    "Order " + order.id() + " has already been fully refunded ($" + order.total() + "); no further refund is allowed.");
        }

        if (refundRequest.amount().compareTo(remaining) > 0) {
            throw new PolicyViolationException(
                    "Refund of $" + refundRequest.amount() + " exceeds the $" + remaining
                            + " remaining refundable on order " + order.id() + ".");
        }
    }
}
