package com.example.orderagent.service.governance;

import com.example.orderagent.config.AgentProperties;
import com.example.orderagent.exception.BreakerOpenException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks consecutive failures per tool and decides when to stop trying.
 *
 * <p>One counter per tool name. A success wipes it — see
 * {@link #recordSuccess}. Once the count reaches the configured threshold,
 * the tool is considered open: {@link #assertClosed} starts throwing, and
 * {@code AgentLoop} turns that into an {@code ESCALATED} response instead
 * of spending another token on a dependency that isn't coming back.
 *
 * <p><b>How it fails:</b> it doesn't — this class only counts and reports.
 * The decision to stop is {@code AgentLoop}'s, made from what this class
 * tells it.
 *
 * <p>This is deliberately a hand-rolled counter, not Resilience4j's
 * {@code CircuitBreaker}. The pattern — count, threshold, open, escalate —
 * is the point of this project; a production system should reach for a
 * maintained library instead of a bespoke implementation like this one.
 * See {@code docs/concepts/03-circuit-breaker.md} for the reasoning.
 */
@Component
public class BreakerRegistry {

    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final int threshold;

    public BreakerRegistry(AgentProperties properties) {
        this.threshold = properties.breakerThreshold();
    }

    /**
     * Throws if the named tool's breaker is currently open.
     *
     * @param toolName the tool about to be called
     * @throws BreakerOpenException if the breaker is open
     */
    public void assertClosed(String toolName) {
        if (isOpen(toolName)) {
            throw new BreakerOpenException(toolName);
        }
    }

    public boolean isOpen(String toolName) {
        return consecutiveFailures.getOrDefault(toolName, 0) >= threshold;
    }

    public void recordFailure(String toolName) {
        consecutiveFailures.merge(toolName, 1, Integer::sum);
    }

    public void recordSuccess(String toolName) {
        // Reset, not decrement. One success means the tool is healthy right
        // now, and that's what we care about. Decrementing would let a tool
        // that fails 2 out of every 3 calls stay closed forever.
        consecutiveFailures.remove(toolName);
    }
}
