package com.example.orderagent.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.orderagent.config.AgentProperties;
import com.example.orderagent.config.DemoProperties;
import com.example.orderagent.exception.BreakerOpenException;
import com.example.orderagent.service.AuditService;
import com.example.orderagent.service.OrderService;
import com.example.orderagent.service.tool.IssueRefundTool;
import com.example.orderagent.service.tool.LookupOrderTool;
import com.example.orderagent.service.tool.OrderAgentTool;
import com.example.orderagent.service.tool.SubmitAnswerTool;
import com.example.orderagent.service.tool.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * With {@code orderagent.demo.fail-refund-tool=true}, {@code issueRefund}
 * throws on every call. This exercises the real chain — {@code ToolRegistry},
 * {@code AuditInterceptor}, and {@code BreakerRegistry} together — proving
 * three consecutive failures open the breaker for that tool, exactly as
 * {@code AgentLoop} relies on to escalate instead of retrying forever. This
 * is the Phase 4 gate's "3 failures produce ESCALATED" case; the
 * loop-level escalation itself is covered by
 * {@code AgentLoopTest#escalatesWhenTheLastToolsBreakerIsAlreadyOpen}.
 */
class CircuitBreakerTest {

    @Test
    void threeConsecutiveFailuresOpenTheBreakerForThatTool() {
        OrderService orderService = mock(OrderService.class);
        AuditService auditService = mock(AuditService.class);
        AgentProperties properties = new AgentProperties(6, 20000, 30, 3);
        BreakerRegistry breakerRegistry = new BreakerRegistry(properties);

        List<OrderAgentTool<?, ?>> tools = List.of(
                new LookupOrderTool(orderService),
                new IssueRefundTool(orderService, new DemoProperties(true, false)), // fault injection on
                new SubmitAnswerTool());
        List<ToolInterceptor> interceptors = List.of(new AuditInterceptor(auditService, breakerRegistry));
        ToolRegistry registry = new ToolRegistry(tools, interceptors, JsonMapper.builder().build());

        String args = "{\"orderId\": 1002, \"amount\": 10.00, \"reason\": \"r\", \"idempotencyKey\": \"key-00000001\"}";

        assertThat(breakerRegistry.isOpen("issueRefund")).isFalse();

        // First two failures: the tool call itself still returns normally
        // (as a structured failure the model can see), and the breaker
        // stays closed.
        String first = registry.invoke("issueRefund", args);
        assertThat(first).contains("\"failed\":true");
        assertThat(breakerRegistry.isOpen("issueRefund")).isFalse();

        registry.invoke("issueRefund", args);
        assertThat(breakerRegistry.isOpen("issueRefund")).isFalse();

        // Third consecutive failure trips it.
        registry.invoke("issueRefund", args);
        assertThat(breakerRegistry.isOpen("issueRefund")).isTrue();

        assertThatThrownBy(() -> breakerRegistry.assertClosed("issueRefund")).isInstanceOf(BreakerOpenException.class);

        // A different tool's breaker is unaffected — the count is per tool.
        assertThat(breakerRegistry.isOpen("lookupOrder")).isFalse();
    }

    @Test
    void oneSuccessResetsTheFailureCount() {
        OrderService orderService = mock(OrderService.class);
        AuditService auditService = mock(AuditService.class);
        AgentProperties properties = new AgentProperties(6, 20000, 30, 3);
        BreakerRegistry breakerRegistry = new BreakerRegistry(properties);
        AuditInterceptor auditInterceptor = new AuditInterceptor(auditService, breakerRegistry);

        breakerRegistry.recordFailure("issueRefund");
        breakerRegistry.recordFailure("issueRefund");

        auditInterceptor.postInvoke(
                new ToolCallContext("trace-1", "issueRefund", null), new ToolOutcome.Success("ok"));

        assertThat(breakerRegistry.isOpen("issueRefund")).isFalse();
        breakerRegistry.recordFailure("issueRefund");
        breakerRegistry.recordFailure("issueRefund");
        // Two more failures after the reset: still below the threshold of 3.
        assertThat(breakerRegistry.isOpen("issueRefund")).isFalse();
    }
}
