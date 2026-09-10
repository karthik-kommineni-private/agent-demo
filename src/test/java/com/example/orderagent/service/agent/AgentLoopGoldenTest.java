package com.example.orderagent.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.orderagent.config.AgentProperties;
import com.example.orderagent.config.DemoProperties;
import com.example.orderagent.config.PriceTableProperties;
import com.example.orderagent.dto.response.AgentResponse;
import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.enums.AgentStatus;
import com.example.orderagent.enums.OrderStatus;
import com.example.orderagent.service.AuditService;
import com.example.orderagent.service.OrderService;
import com.example.orderagent.service.governance.AllowlistInterceptor;
import com.example.orderagent.service.governance.AuditInterceptor;
import com.example.orderagent.service.governance.BreakerRegistry;
import com.example.orderagent.service.governance.PolicyInterceptor;
import com.example.orderagent.service.governance.RedactionInterceptor;
import com.example.orderagent.service.governance.ToolInterceptor;
import com.example.orderagent.service.tool.IssueRefundTool;
import com.example.orderagent.service.tool.LookupOrderTool;
import com.example.orderagent.service.tool.OrderAgentTool;
import com.example.orderagent.service.tool.SubmitAnswerTool;
import com.example.orderagent.service.tool.ToolRegistry;
import com.example.orderagent.testsupport.CassetteChatModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replays a cassette recorded from a real Anthropic call (see
 * {@code testsupport/CassetteRecordingTool}) through the real
 * {@link AgentLoop}, a real {@link ToolCallingManager}, the real
 * {@link ToolRegistry}, and the real governance chain — only the model
 * call itself is canned. This is the Phase 6 gate: a fixed input
 * resolving to an asserted tool sequence and final output,
 * deterministically and with zero API calls.
 *
 * <p>This is a meaningfully different test from {@link AgentLoopTest},
 * which mocks {@code ToolCallingManager} itself to isolate the loop's own
 * control flow. Here, everything downstream of the model response is the
 * real production code, proving the whole request-to-tool-execution path
 * reproduces the same recorded outcome every time.
 */
class AgentLoopGoldenTest {

    @Test
    void happyPathLookupCassetteResolvesToTheRecordedAnswer() {
        OrderService orderService = mock(OrderService.class);
        OrderDto order1001 = new OrderDto(
                1001L, "CUST-1001", "alice@example.com", new BigDecimal("89.99"),
                OrderStatus.DELIVERED, LocalDate.of(2026, 8, 14), BigDecimal.ZERO, "on time");
        when(orderService.findById(1001L)).thenReturn(Optional.of(order1001));

        List<OrderAgentTool<?, ?>> tools = List.of(
                new LookupOrderTool(orderService),
                new IssueRefundTool(orderService, new DemoProperties(false, false)),
                new SubmitAnswerTool());

        AuditService auditService = mock(AuditService.class);
        AgentProperties properties = new AgentProperties(6, 20000, 30, 3);
        BreakerRegistry breakerRegistry = new BreakerRegistry(properties);
        List<ToolInterceptor> interceptors = List.of(
                new AllowlistInterceptor(tools, new DemoProperties(false, false)),
                new PolicyInterceptor(orderService),
                new RedactionInterceptor(JsonMapper.builder().build()),
                new AuditInterceptor(auditService, breakerRegistry));

        ToolRegistry toolRegistry = new ToolRegistry(tools, interceptors, JsonMapper.builder().build());
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
        CassetteChatModel cassetteChatModel = CassetteChatModel.loadFromClasspath("cassettes/happy-path-lookup.json");
        SystemPromptLoader promptLoader =
                new SystemPromptLoader(new ClassPathResource("prompts/order-agent-system-prompt.md"));
        TokenMeter tokenMeter = new TokenMeter(
                new PriceTableProperties(Map.of(
                        "claude-haiku-4-5-20251001",
                        new PriceTableProperties.ModelPrice(new BigDecimal("1.00"), new BigDecimal("5.00")))),
                new SimpleMeterRegistry());

        AgentLoop agentLoop = new AgentLoop(
                cassetteChatModel,
                toolCallingManager,
                toolRegistry,
                promptLoader,
                properties,
                JsonMapper.builder().build(),
                breakerRegistry,
                tokenMeter);

        AgentResponse response = agentLoop.run("Where is order 1001?");

        assertThat(response.status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(response.message()).contains("1001").containsIgnoringCase("delivered");
        assertThat(response.iterations()).isEqualTo(2);
        assertThat(cassetteChatModel.callCount()).isEqualTo(2);
        assertThat(response.trace().steps())
                .extracting(step -> step.toolName())
                .containsExactly("lookupOrder", "submit_answer");
        assertThat(response.cost()).isGreaterThan(BigDecimal.ZERO);
    }
}
