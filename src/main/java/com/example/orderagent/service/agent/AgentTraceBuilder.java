package com.example.orderagent.service.agent;

import com.example.orderagent.dto.response.AgentTrace;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the tool calls made during one pass through {@link AgentLoop},
 * then assembles them into an immutable {@link AgentTrace}.
 *
 * <p>A mutable builder rather than an immutable trace built up in place
 * because the loop doesn't know in advance how many iterations a request
 * will take — the same reason {@code AgentLoop} itself is a loop and not a
 * fixed sequence.
 */
class AgentTraceBuilder {

    private final String traceId;
    private final String promptVersion;
    private final List<AgentTrace.Step> steps = new ArrayList<>();

    AgentTraceBuilder(String traceId, String promptVersion) {
        this.traceId = traceId;
        this.promptVersion = promptVersion;
    }

    void recordToolCall(int iteration, String toolName, String argumentsJson) {
        steps.add(new AgentTrace.Step(iteration, toolName, argumentsJson));
    }

    AgentTrace build() {
        return new AgentTrace(traceId, promptVersion, List.copyOf(steps));
    }
}
