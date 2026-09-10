package com.example.orderagent.dto.response;

import com.example.orderagent.enums.AgentStatus;
import com.example.orderagent.enums.TerminationReason;
import java.math.BigDecimal;

/**
 * The one response shape every request produces — success, blocked,
 * escalated, or error all return this, never a raw exception. See
 * CLAUDE.md non-negotiable rule 5.
 *
 * @param status           the outcome category
 * @param message          the customer-facing answer, present only on {@link AgentStatus#SUCCESS}
 * @param trace            every tool call made while resolving this request
 * @param iterations       how many passes through the loop this request took
 * @param totalTokens      prompt + completion tokens across every model call this request made
 * @param cost             the dollar cost of those tokens; {@code null} until Phase 5 wires up
 *                         {@code TokenMeter} and {@code PriceTable}
 * @param terminationReason why the loop stopped
 * @param modelUsed        the model name reported by the last model response
 */
public record AgentResponse(
        AgentStatus status,
        String message,
        AgentTrace trace,
        int iterations,
        int totalTokens,
        BigDecimal cost,
        TerminationReason terminationReason,
        String modelUsed) {

    public static AgentResponse success(
            String message, AgentTrace trace, int iterations, int totalTokens, String modelUsed) {
        return new AgentResponse(
                AgentStatus.SUCCESS, message, trace, iterations, totalTokens, null, TerminationReason.COMPLETED, modelUsed);
    }

    public static AgentResponse escalated(
            AgentTrace trace, int iterations, int totalTokens, TerminationReason reason, String modelUsed) {
        return new AgentResponse(AgentStatus.ESCALATED, null, trace, iterations, totalTokens, null, reason, modelUsed);
    }

    public static AgentResponse error(AgentTrace trace, int iterations, int totalTokens, String message, String modelUsed) {
        return new AgentResponse(AgentStatus.ERROR, message, trace, iterations, totalTokens, null, null, modelUsed);
    }
}
