package com.example.orderagent.service.tool;

/**
 * A single capability the model can invoke: a name and description the
 * model sees, a strict input schema describing exactly what arguments it
 * must supply, and the domain logic that runs once those arguments have
 * been validated.
 *
 * <p>Implementations do domain work only. Allowlisting, policy caps,
 * redaction, auditing and breaker registration all happen around a call to
 * this tool, in {@code service.governance} — never inside it. See
 * {@link ToolRegistry} for where arguments are validated against
 * {@link #inputSchema()} before {@link #execute} ever runs.
 *
 * @param <I> the tool's validated input shape
 * @param <O> the tool's output shape, serialized to JSON for the model
 */
public interface OrderAgentTool<I, O> {

    /** The name the model calls this tool by. Must be unique in the registry. */
    String name();

    /** A plain-language description of what this tool does, shown to the model. */
    String description();

    /**
     * The JSON Schema describing this tool's arguments, sent to the model
     * as part of its tool definitions.
     *
     * <p>Always declares {@code "additionalProperties": false}. That's a
     * hint to the model, not an enforced constraint — nothing on the wire
     * stops a model from sending an extra field anyway — so
     * {@link ToolRegistry} separately re-validates every call against
     * {@link #inputType()} with a parser that rejects unknown fields for
     * real.
     */
    String inputSchema();

    /** The Java type {@link ToolRegistry} deserializes arguments into. */
    Class<I> inputType();

    /**
     * Whether calling this tool ends the agent loop.
     *
     * <p>True only for {@code submit_answer}. {@link ToolRegistry} marks
     * such a tool's callback as {@code returnDirect} so
     * {@code ToolCallingManager} reports it back to {@code AgentLoop},
     * which is how the loop tells "the model is done" apart from "the
     * model wants to keep working" without special-casing a tool name.
     */
    default boolean endsTheLoop() {
        return false;
    }

    /**
     * Whether this tool changes state (versus a read-only lookup).
     *
     * <p>True only for {@code issueRefund}. Drives two governance
     * decisions: {@code AllowlistInterceptor} blocks it outright in
     * dry-run mode, and it's the class of tool non-negotiable rule 2
     * requires a pre-hook and breaker registration for.
     */
    default boolean isWriteOperation() {
        return false;
    }

    /**
     * Runs this tool's domain logic against already-validated input.
     *
     * @param input the deserialized, schema-checked arguments
     * @return the tool's result, to be serialized back to the model
     */
    O execute(I input);
}
