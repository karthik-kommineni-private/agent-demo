package com.example.orderagent.enums;

/**
 * The outcome of one agent request, carried on every {@code AgentResponse}.
 */
public enum AgentStatus {
    /** The agent finished normally and produced an answer. */
    SUCCESS,
    /** A governance check refused to let a tool run (Phase 4). */
    BLOCKED,
    /** The loop stopped itself — a cap, a breaker, or the model being
     *  unreachable — and handed the request to a human. */
    ESCALATED,
    /** Something failed that isn't one of the above: the model didn't
     *  follow the tool-calling contract, or a tool call failed in a way
     *  that wasn't a policy block. */
    ERROR
}
