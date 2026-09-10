package com.example.orderagent.service.tool;

import com.example.orderagent.dto.tool.ToolBlocked;
import com.example.orderagent.dto.tool.ToolFailed;
import com.example.orderagent.exception.BreakerOpenException;
import com.example.orderagent.exception.PolicyViolationException;
import com.example.orderagent.exception.ToolExecutionException;
import com.example.orderagent.service.governance.ToolCallContext;
import com.example.orderagent.service.governance.ToolInterceptor;
import com.example.orderagent.service.governance.ToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Maps a tool name to its schema and handler, and is the single point
 * through which every tool call actually runs.
 *
 * <p>"Single point" is deliberate: the governance interceptor chain
 * ({@code service.governance}) runs entirely inside {@link #invoke}, not
 * around calls to the individual tool classes. There is no path to a tool
 * that skips the guardrails, because there is no other way to reach one.
 *
 * <p><b>Why arguments are re-validated here instead of trusted from the
 * model:</b> a tool's JSON Schema — see {@link OrderAgentTool#inputSchema()}
 * — is only ever a hint the model reads. Nothing on the wire enforces
 * {@code additionalProperties: false}. This class re-parses every call's
 * arguments with a strict Jackson mapper that fails on an unrecognized
 * field, so "the model sent an extra field" is a real, testable rejection
 * rather than a suggestion the model is free to ignore.
 *
 * @see com.example.orderagent.service.governance.ToolInterceptor for the
 *      guardrails run around every call
 */
@Component
public class ToolRegistry {

    private final Map<String, OrderAgentTool<Object, Object>> toolsByName;
    private final List<ToolInterceptor> interceptors;
    private final ObjectMapper strictMapper;
    private final ObjectMapper outputMapper;

    @SuppressWarnings("unchecked")
    public ToolRegistry(List<OrderAgentTool<?, ?>> tools, List<ToolInterceptor> interceptors, ObjectMapper objectMapper) {
        this.toolsByName = tools.stream()
                .map(tool -> (OrderAgentTool<Object, Object>) tool)
                .collect(Collectors.toMap(OrderAgentTool::name, Function.identity()));
        // Spring injects List<ToolInterceptor> already ordered by @Order —
        // Allowlist, then Policy, then Redaction, then Audit. That order is
        // load-bearing: see each interceptor's class Javadoc for why.
        this.interceptors = interceptors;
        // A dedicated copy of the app's ObjectMapper so tool-argument parsing
        // stays strict (rejects unknown fields) without changing how JSON is
        // handled anywhere else in the app.
        this.strictMapper = objectMapper.rebuild().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true).build();
        this.outputMapper = objectMapper;
    }

    /** All registered tools, for building the tool list sent to the model. */
    public List<OrderAgentTool<Object, Object>> tools() {
        return List.copyOf(toolsByName.values());
    }

    /**
     * Looks up a tool by name.
     *
     * @param name the tool name
     * @return the tool
     * @throws ToolExecutionException if no tool is registered under that name
     */
    public OrderAgentTool<Object, Object> get(String name) {
        OrderAgentTool<Object, Object> tool = toolsByName.get(name);
        if (tool == null) {
            throw new ToolExecutionException(name, "No such tool: " + name);
        }
        return tool;
    }

    /**
     * {@link #invoke(String, String, String)} with no trace id, for tests
     * and other callers that don't need the call correlated to a request.
     */
    public String invoke(String toolName, String argumentsJson) {
        return invoke("untraced", toolName, argumentsJson);
    }

    /**
     * Parses {@code argumentsJson}, runs the governance interceptor chain,
     * executes the tool if nothing blocked it, and returns a JSON result —
     * the tool's real output, or a structured block/failure the model can
     * read and explain.
     *
     * @param traceId       the id correlating this call to one agent request
     * @param toolName      the tool to run, as named in {@link OrderAgentTool#name()}
     * @param argumentsJson the model-supplied arguments, as a raw JSON object string
     * @return the tool's output, or a {@code ToolBlocked}/{@code ToolFailed}
     *         payload, serialized to JSON
     * @throws ToolExecutionException if the tool is unknown or the arguments
     *         don't match its schema — a contract violation, not a runtime
     *         outcome, so it isn't shaped into JSON for the model
     */
    public String invoke(String traceId, String toolName, String argumentsJson) {
        OrderAgentTool<Object, Object> tool = get(toolName);
        Object input = parseArguments(tool, argumentsJson);
        ToolCallContext ctx = new ToolCallContext(traceId, toolName, input);

        try {
            for (ToolInterceptor interceptor : interceptors) {
                interceptor.preInvoke(ctx);
            }
        } catch (PolicyViolationException | BreakerOpenException e) {
            runPostHooks(ctx, new ToolOutcome.Blocked(e.getMessage()));
            return serialize(new ToolBlocked(e.getMessage()));
        }

        ToolOutcome outcome;
        Object output = null;
        try {
            output = tool.execute(input);
            outcome = new ToolOutcome.Success(output);
        } catch (RuntimeException e) {
            outcome = new ToolOutcome.Failure(e);
        }

        runPostHooks(ctx, outcome);

        if (outcome instanceof ToolOutcome.Failure failure) {
            return serialize(new ToolFailed(failure.exception().getMessage()));
        }
        return serialize(output);
    }

    private void runPostHooks(ToolCallContext ctx, ToolOutcome outcome) {
        for (ToolInterceptor interceptor : interceptors) {
            interceptor.postInvoke(ctx, outcome);
        }
    }

    private Object parseArguments(OrderAgentTool<Object, Object> tool, String argumentsJson) {
        try {
            return strictMapper.readValue(argumentsJson, tool.inputType());
        } catch (UnrecognizedPropertyException e) {
            throw new ToolExecutionException(
                    tool.name(), "Unrecognized argument \"" + e.getPropertyName() + "\" for tool " + tool.name(), e);
        } catch (Exception e) {
            throw new ToolExecutionException(
                    tool.name(), "Invalid arguments for tool " + tool.name() + ": " + e.getMessage(), e);
        }
    }

    private String serialize(Object value) {
        try {
            return outputMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ToolExecutionException("unknown", "Failed to serialize tool result", e);
        }
    }

    /**
     * Adapts every registered tool into a Spring AI {@link ToolCallback},
     * bound to one request's trace id, for attaching to a {@code Prompt}'s
     * chat options.
     *
     * <p>Every callback routes through {@link #invoke(String, String, String)}
     * — there is deliberately no other implementation of the actual call
     * logic, so {@code ToolCallingManager} executing a tool and a test
     * calling {@link #invoke} directly exercise the exact same code path.
     */
    public List<ToolCallback> toolCallbacks(String traceId) {
        return tools().stream().map(tool -> asToolCallback(tool, traceId)).toList();
    }

    private ToolCallback asToolCallback(OrderAgentTool<Object, Object> tool, String traceId) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();
        ToolMetadata metadata = ToolMetadata.builder().returnDirect(tool.endsTheLoop()).build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String toolInput) {
                return invoke(traceId, tool.name(), toolInput);
            }
        };
    }
}
