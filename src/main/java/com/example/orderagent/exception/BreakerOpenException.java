package com.example.orderagent.exception;

/**
 * Thrown by {@code BreakerRegistry} when a tool's consecutive-failure count
 * has crossed the threshold and the breaker has opened for it.
 *
 * <p>{@code AgentLoop} catches this at the top of each iteration — before
 * spending a token on another model call — and turns it into an
 * {@code ESCALATED} response with {@code TerminationReason.BREAKER_OPEN}.
 * See docs/concepts/03-circuit-breaker.md.
 */
public class BreakerOpenException extends RuntimeException {

    public BreakerOpenException(String toolName) {
        super("Circuit breaker is open for tool: " + toolName);
    }
}
