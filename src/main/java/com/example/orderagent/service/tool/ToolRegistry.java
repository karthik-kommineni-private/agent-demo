package com.example.orderagent.service.tool;

import com.example.orderagent.exception.ToolExecutionException;
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
 * <p>"Single point" is deliberate: from Phase 4 on, the governance
 * interceptor chain wraps calls to {@link #invoke(String, String)}, not
 * calls to the individual tool classes. There is no path to a tool that
 * skips the guardrails, because there is no other way to reach one.
 *
 * <p><b>Why arguments are re-validated here instead of trusted from the
 * model:</b> a tool's JSON Schema — see {@link OrderAgentTool#inputSchema()}
 * — is only ever a hint the model reads. Nothing on the wire enforces
 * {@code additionalProperties: false}. This class re-parses every call's
 * arguments with a strict Jackson mapper that fails on an unrecognized
 * field, so "the model sent an extra field" is a real, testable rejection
 * rather than a suggestion the model is free to ignore.
 */
@Component
public class ToolRegistry {

    private final Map<String, OrderAgentTool<Object, Object>> toolsByName;
    private final ObjectMapper strictMapper;
    private final ObjectMapper outputMapper;

    @SuppressWarnings("unchecked")
    public ToolRegistry(List<OrderAgentTool<?, ?>> tools, ObjectMapper objectMapper) {
        this.toolsByName = tools.stream()
                .map(tool -> (OrderAgentTool<Object, Object>) tool)
                .collect(Collectors.toMap(OrderAgentTool::name, Function.identity()));
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
     * Parses {@code argumentsJson} against the named tool's input type,
     * runs the tool, and returns its output serialized back to JSON.
     *
     * @param toolName      the tool to run, as named in {@link OrderAgentTool#name()}
     * @param argumentsJson the model-supplied arguments, as a raw JSON object string
     * @return the tool's output, serialized to JSON
     * @throws ToolExecutionException if the tool is unknown, the arguments
     *         don't match its schema (an unrecognized field included), or
     *         the tool's own execution fails
     */
    public String invoke(String toolName, String argumentsJson) {
        OrderAgentTool<Object, Object> tool = get(toolName);

        Object input;
        try {
            input = strictMapper.readValue(argumentsJson, tool.inputType());
        } catch (UnrecognizedPropertyException e) {
            throw new ToolExecutionException(
                    toolName, "Unrecognized argument \"" + e.getPropertyName() + "\" for tool " + toolName, e);
        } catch (Exception e) {
            throw new ToolExecutionException(
                    toolName, "Invalid arguments for tool " + toolName + ": " + e.getMessage(), e);
        }

        Object output = tool.execute(input);

        try {
            return outputMapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new ToolExecutionException(toolName, "Failed to serialize output of tool " + toolName, e);
        }
    }

    /**
     * Adapts every registered tool into a Spring AI {@link ToolCallback},
     * for attaching to a {@code Prompt}'s chat options.
     *
     * <p>Every callback routes through {@link #invoke(String, String)} —
     * there is deliberately no other implementation of the actual call
     * logic, so {@code ToolCallingManager} executing a tool and a test
     * calling {@link #invoke} directly exercise the exact same code path.
     */
    public List<ToolCallback> toolCallbacks() {
        return tools().stream().map(this::asToolCallback).toList();
    }

    private ToolCallback asToolCallback(OrderAgentTool<Object, Object> tool) {
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
                return invoke(tool.name(), toolInput);
            }
        };
    }
}
