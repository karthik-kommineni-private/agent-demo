package com.example.orderagent.exception;

/**
 * Thrown when a tool cannot be executed as requested — an unknown tool
 * name, arguments that don't match the tool's schema, or a failure inside
 * the tool's own domain logic.
 *
 * <p>Distinct from a policy violation: this means "the request was
 * malformed or the tool broke," not "the request was well-formed but
 * against policy." {@code AgentLoop} converts both into a message the
 * model can see so it can recover, but only this one counts toward
 * {@code BreakerRegistry}'s consecutive-failure total, since a policy
 * block isn't a signal that the tool itself is unhealthy.
 */
public class ToolExecutionException extends RuntimeException {

    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
