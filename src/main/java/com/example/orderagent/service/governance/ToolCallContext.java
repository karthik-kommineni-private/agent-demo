package com.example.orderagent.service.governance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything a governance interceptor needs to know about one tool call,
 * plus a scratch space for passing data between interceptors.
 *
 * <p>{@code attributes} exists specifically so {@code RedactionInterceptor}
 * can hand a redacted copy of the result to {@code AuditInterceptor}
 * without the two classes depending on each other directly — the same
 * request-attribute pattern a servlet filter chain uses. Ordering matters
 * here: {@code RedactionInterceptor} must run before {@code AuditInterceptor}
 * in the interceptor list, or there is nothing in {@code attributes} yet
 * for the audit row to read.
 *
 * @param traceId the id shared by every tool call, log line and audit row
 *                produced while resolving one agent request
 * @param toolName the tool being called
 * @param input    the already-validated, deserialized arguments
 */
public record ToolCallContext(String traceId, String toolName, Object input, Map<String, Object> attributes) {

    public ToolCallContext(String traceId, String toolName, Object input) {
        this(traceId, toolName, input, new ConcurrentHashMap<>());
    }
}
