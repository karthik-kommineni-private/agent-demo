package com.example.orderagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The bounds {@code AgentLoop} enforces on every request, all from
 * {@code application.yml} under {@code orderagent.*} — never a magic
 * number in {@code AgentLoop} itself, so changing a limit is a config
 * change, not a code change.
 *
 * @param maxIterations           hard cap on passes through the loop before it
 *                                escalates with {@code ITERATION_CAP}
 * @param tokenBudget             hard cap on prompt + completion tokens across a
 *                                single request before it escalates with
 *                                {@code BUDGET_CAP}
 * @param modelCallTimeoutSeconds how long a single model call may run before
 *                                the loop gives up on it and escalates with
 *                                {@code MODEL_UNAVAILABLE}
 * @param breakerThreshold        consecutive failures on the same tool before
 *                                {@code BreakerRegistry} opens it and the loop
 *                                escalates with {@code BREAKER_OPEN}
 */
@ConfigurationProperties(prefix = "orderagent")
public record AgentProperties(
        @DefaultValue("6") int maxIterations,
        @DefaultValue("20000") int tokenBudget,
        @DefaultValue("30") int modelCallTimeoutSeconds,
        @DefaultValue("3") int breakerThreshold) {
}
