package com.example.orderagent.exception;

/**
 * Thrown by a governance pre-hook to block a tool call the model requested
 * but that policy does not allow — a refund over the order's remaining
 * refundable balance, an order that's already fully refunded, or a tool
 * not on the allowlist.
 *
 * <p>Distinct from {@link ToolExecutionException}: this is "the request
 * was well-formed but against policy," not "the request was malformed or
 * the tool broke." {@code ToolRegistry} catches this and turns it into a
 * normal tool result the model can read and explain to the customer,
 * rather than letting it propagate as a failure — a policy block is an
 * expected, structured outcome, not an error.
 */
public class PolicyViolationException extends RuntimeException {

    public PolicyViolationException(String message) {
        super(message);
    }
}
