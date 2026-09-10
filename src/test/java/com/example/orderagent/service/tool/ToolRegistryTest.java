package com.example.orderagent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.orderagent.config.DemoProperties;
import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.enums.OrderStatus;
import com.example.orderagent.exception.ToolExecutionException;
import com.example.orderagent.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises {@link ToolRegistry} as the single execution point every tool
 * call goes through — this is the Phase 2 gate: each tool callable
 * directly, and its schema actually enforced, not just documented.
 */
@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @Mock
    private OrderService orderService;

    private ToolRegistry registry() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        List<OrderAgentTool<?, ?>> tools = List.of(
                new LookupOrderTool(orderService),
                new IssueRefundTool(orderService, new DemoProperties(false, false)),
                new SubmitAnswerTool());
        // No governance interceptors here on purpose — this test is about
        // ToolRegistry's own contract (schema validation, unknown tools),
        // covered separately in service.governance's Guardrail*Test classes.
        return new ToolRegistry(tools, List.of(), objectMapper);
    }

    @Test
    void invokesLookupOrderAndReturnsJson() {
        OrderDto order = new OrderDto(
                1001L, "CUST-1001", "alice@example.com", new BigDecimal("89.99"),
                OrderStatus.DELIVERED, LocalDate.of(2026, 8, 14), BigDecimal.ZERO, "on time");
        when(orderService.findById(1001L)).thenReturn(Optional.of(order));

        String json = registry().invoke("lookupOrder", "{\"orderId\": 1001}");

        assertThat(json).contains("\"orderId\":1001").contains("\"status\":\"DELIVERED\"");
    }

    @Test
    void rejectsAnUnrecognizedArgument() {
        // "orderIdentifier" isn't in LookupOrderInput's schema — the strict
        // mapper must fail closed rather than silently dropping it.
        assertThatThrownBy(() -> registry().invoke("lookupOrder", "{\"orderIdentifier\": 1001}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("orderIdentifier");
    }

    @Test
    void rejectsAnUnknownToolName() {
        assertThatThrownBy(() -> registry().invoke("deleteOrder", "{}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("deleteOrder");
    }

    @Test
    void registersAllThreeTools() {
        assertThat(registry().tools())
                .extracting(OrderAgentTool::name)
                .containsExactlyInAnyOrder("lookupOrder", "issueRefund", "submit_answer");
    }
}
