package com.example.orderagent.enums;

/**
 * Why {@code AgentLoop} stopped. Distinct from {@link AgentStatus}: this is
 * the specific trigger, status is the outcome category it maps to.
 */
public enum TerminationReason {
    /** The model called {@code submit_answer} and the loop returned it. */
    COMPLETED,
    /** A governance pre-hook blocked a tool call (Phase 4). */
    POLICY_BLOCK,
    /** A tool's consecutive-failure count tripped the breaker (Phase 4). */
    BREAKER_OPEN,
    /** The loop hit its hard iteration cap without a final answer. */
    ITERATION_CAP,
    /** The loop's token budget for this request was spent. */
    BUDGET_CAP,
    /** The model call itself failed or timed out — a dependency failure,
     *  not a decision the loop or the model made. */
    MODEL_UNAVAILABLE
}
