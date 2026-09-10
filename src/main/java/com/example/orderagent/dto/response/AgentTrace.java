package com.example.orderagent.dto.response;

import java.util.List;

/**
 * The recorded sequence of tool calls made while resolving one request.
 *
 * <p>Exists so a multi-step decision — "look up the order, notice it
 * shipped late, then refund it" — is visible after the fact, not just
 * something the model did once and forgot. One trace ID flows through the
 * loop, every tool call and (from Phase 4) every audit row, so this and the
 * audit log can be cross-referenced by {@code traceId}.
 *
 * @param traceId       the id shared by every log line, tool call and audit
 *                       row produced while resolving this request
 * @param promptVersion the version header of the system prompt used, so a
 *                       later prompt change doesn't silently reinterpret an
 *                       old trace
 * @param steps         each tool call made, in the order it happened
 */
public record AgentTrace(String traceId, String promptVersion, List<Step> steps) {

    /**
     * One tool call within a trace.
     *
     * @param iteration     which pass through the loop this happened on, starting at 1
     * @param toolName      the tool that was called
     * @param argumentsJson the raw arguments the model supplied
     */
    public record Step(int iteration, String toolName, String argumentsJson) {
    }
}
