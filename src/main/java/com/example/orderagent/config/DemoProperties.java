package com.example.orderagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Flags that exist only to make guardrails demonstrable, never touched in
 * a normal run. Kept separate from {@link AgentProperties} so it's obvious
 * at a glance which config is "how the agent behaves" and which is "what
 * we're pretending is broken for this demo."
 *
 * @param failRefundTool when true, {@code issueRefund} always throws — for
 *                        showing the circuit breaker open after three
 *                        consecutive failures
 * @param dryRunMode      when true, {@code issueRefund} is blocked entirely
 *                        (a normal policy block, not an error) so a demo can
 *                        run against real data without ever writing to it
 */
@ConfigurationProperties(prefix = "orderagent.demo")
public record DemoProperties(@DefaultValue("false") boolean failRefundTool, @DefaultValue("false") boolean dryRunMode) {
}
