package com.example.orderagent.service.governance;

/**
 * A guardrail that runs around every tool call, regardless of what the
 * model decided. This is the mechanism behind non-negotiable rule 1:
 * policy lives here, in code, never in the prompt.
 *
 * <p>Implementations are registered as a single ordered list (see
 * {@code @Order} on each implementation) and all run around every tool
 * call — there is no per-tool interceptor wiring to get wrong. A tool that
 * doesn't need a given check simply isn't affected by it (a read-only
 * lookup, for instance, has nothing for {@code PolicyInterceptor} to
 * object to).
 *
 * @see com.example.orderagent.service.tool.ToolRegistry for where this
 *      chain is actually run
 */
public interface ToolInterceptor {

    /**
     * Checks whether this tool call is allowed to run, before it runs.
     *
     * <p>Throwing {@link com.example.orderagent.exception.PolicyViolationException}
     * stops the chain right there — nothing has executed yet, so a block
     * costs nothing but a few milliseconds. The exception message becomes
     * the reason handed back to the <i>model</i>, not just the logs, so it
     * can explain the refusal to the customer instead of silently retrying.
     *
     * @param ctx the tool call being considered
     */
    default void preInvoke(ToolCallContext ctx) {
        // no-op: most interceptors only care about one phase
    }

    /**
     * Observes what happened after a tool call executed — success or
     * failure. Runs whether the call succeeded or not, which is why
     * failure counting ({@code AuditInterceptor}) and audit logging live in
     * the same hook rather than a separate one.
     *
     * @param ctx     the tool call that ran
     * @param outcome what happened
     */
    default void postInvoke(ToolCallContext ctx, ToolOutcome outcome) {
        // no-op: most interceptors only care about one phase
    }
}
