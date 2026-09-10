package com.example.orderagent.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.dto.tool.IssueRefundInput;
import com.example.orderagent.dto.tool.LookupOrderInput;
import com.example.orderagent.enums.OrderStatus;
import com.example.orderagent.exception.PolicyViolationException;
import com.example.orderagent.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code PolicyInterceptor} is the guardrail this project exists to
 * demonstrate: a refund request the model decided to make still cannot
 * exceed what's actually left to refund. This is the Phase 4 gate's
 * "over-cap refund blocked" case.
 */
@ExtendWith(MockitoExtension.class)
class GuardrailPolicyTest {

    @Mock
    private OrderService orderService;

    @Test
    void blocksARefundThatExceedsTheOrderTotal() {
        OrderDto order = new OrderDto(
                1002L, "CUST-1002", "bilal@example.com", new BigDecimal("120.00"),
                OrderStatus.SHIPPED, LocalDate.of(2026, 8, 25), BigDecimal.ZERO, "shipped late");
        when(orderService.findById(1002L)).thenReturn(Optional.of(order));

        PolicyInterceptor interceptor = new PolicyInterceptor(orderService);
        ToolCallContext ctx = new ToolCallContext(
                "trace-1", "issueRefund", new IssueRefundInput(1002L, new BigDecimal("5000.00"), "reason", "key-00000001"));

        assertThatThrownBy(() -> interceptor.preInvoke(ctx))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void blocksARefundOnAnAlreadyFullyRefundedOrder() {
        OrderDto order = new OrderDto(
                1003L, "CUST-1003", "carmen@example.com", new BigDecimal("250.00"),
                OrderStatus.DELIVERED, LocalDate.of(2026, 7, 2), new BigDecimal("250.00"), "fully refunded");
        when(orderService.findById(1003L)).thenReturn(Optional.of(order));

        PolicyInterceptor interceptor = new PolicyInterceptor(orderService);
        ToolCallContext ctx = new ToolCallContext(
                "trace-1", "issueRefund", new IssueRefundInput(1003L, new BigDecimal("1.00"), "reason", "key-00000001"));

        assertThatThrownBy(() -> interceptor.preInvoke(ctx))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("already been fully refunded");
    }

    @Test
    void blocksARefundOnAnOrderThatDoesNotExist() {
        when(orderService.findById(9999L)).thenReturn(Optional.empty());

        PolicyInterceptor interceptor = new PolicyInterceptor(orderService);
        ToolCallContext ctx = new ToolCallContext(
                "trace-1", "issueRefund", new IssueRefundInput(9999L, new BigDecimal("1.00"), "reason", "key-00000001"));

        assertThatThrownBy(() -> interceptor.preInvoke(ctx))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void allowsARefundWithinTheRemainingBalance() {
        OrderDto order = new OrderDto(
                1004L, "CUST-1004", "deshi@example.com", new BigDecimal("300.00"),
                OrderStatus.DELIVERED, LocalDate.of(2026, 7, 18), new BigDecimal("100.00"), "partially refunded");
        when(orderService.findById(1004L)).thenReturn(Optional.of(order));

        PolicyInterceptor interceptor = new PolicyInterceptor(orderService);
        ToolCallContext ctx = new ToolCallContext(
                "trace-1", "issueRefund", new IssueRefundInput(1004L, new BigDecimal("200.00"), "reason", "key-00000001"));

        assertThat(ctx).isNotNull();
        interceptor.preInvoke(ctx); // does not throw
    }

    @Test
    void doesNothingForNonRefundTools() {
        PolicyInterceptor interceptor = new PolicyInterceptor(orderService);
        ToolCallContext ctx = new ToolCallContext("trace-1", "lookupOrder", new LookupOrderInput(1002L));

        interceptor.preInvoke(ctx); // does not throw, and never touches orderService
    }
}
