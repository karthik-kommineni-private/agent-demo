package com.example.orderagent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.orderagent.config.DemoProperties;
import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.dto.tool.IssueRefundInput;
import com.example.orderagent.dto.tool.IssueRefundOutput;
import com.example.orderagent.enums.OrderStatus;
import com.example.orderagent.exception.ToolExecutionException;
import com.example.orderagent.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueRefundToolTest {

    private static final String KEY = "refund-attempt-0001";

    @Mock
    private OrderService orderService;

    @Test
    void appliesTheRefundAndReturnsTheUpdatedTotal() {
        OrderDto updated = new OrderDto(
                1002L, "CUST-1002", "bilal@example.com", new BigDecimal("120.00"),
                OrderStatus.SHIPPED, LocalDate.of(2026, 8, 25), new BigDecimal("50.00"), "shipped late");
        when(orderService.applyRefund(1002L, new BigDecimal("50.00"))).thenReturn(Optional.of(updated));

        IssueRefundTool tool = new IssueRefundTool(orderService, new DemoProperties(false, false));
        IssueRefundOutput output = tool.execute(new IssueRefundInput(1002L, new BigDecimal("50.00"), "shipped late", KEY));

        assertThat(output.orderId()).isEqualTo(1002L);
        assertThat(output.amountRefunded()).isEqualByComparingTo("50.00");
        assertThat(output.totalRefunded()).isEqualByComparingTo("50.00");
        assertThat(output.replay()).isFalse();
    }

    @Test
    void replayingTheSameIdempotencyKeyDoesNotRefundTwice() {
        OrderDto updated = new OrderDto(
                1002L, "CUST-1002", "bilal@example.com", new BigDecimal("120.00"),
                OrderStatus.SHIPPED, LocalDate.of(2026, 8, 25), new BigDecimal("50.00"), "shipped late");
        when(orderService.applyRefund(1002L, new BigDecimal("50.00"))).thenReturn(Optional.of(updated));

        IssueRefundTool tool = new IssueRefundTool(orderService, new DemoProperties(false, false));
        IssueRefundInput input = new IssueRefundInput(1002L, new BigDecimal("50.00"), "shipped late", KEY);

        IssueRefundOutput first = tool.execute(input);
        IssueRefundOutput replay = tool.execute(input);

        assertThat(first.replay()).isFalse();
        assertThat(replay.replay()).isTrue();
        assertThat(replay.amountRefunded()).isEqualByComparingTo(first.amountRefunded());
        // applyRefund must only have been called once — the second call was
        // served entirely from the idempotency store.
        verify(orderService).applyRefund(1002L, new BigDecimal("50.00"));
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void rejectsAnInvalidIdempotencyKey() {
        IssueRefundTool tool = new IssueRefundTool(orderService, new DemoProperties(false, false));

        assertThatThrownBy(() -> tool.execute(new IssueRefundInput(1002L, new BigDecimal("50.00"), "reason", "short")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("idempotency key");
    }

    @Test
    void rejectsANonPositiveAmount() {
        IssueRefundTool tool = new IssueRefundTool(orderService, new DemoProperties(false, false));

        assertThatThrownBy(() -> tool.execute(new IssueRefundInput(1002L, BigDecimal.ZERO, "reason", KEY)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void failsEveryCallWhenFaultInjectionIsEnabled() {
        IssueRefundTool tool = new IssueRefundTool(orderService, new DemoProperties(true, false));

        assertThatThrownBy(() -> tool.execute(new IssueRefundInput(1002L, new BigDecimal("10.00"), "reason", KEY)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Simulated failure");
    }

    @Test
    void throwsWhenNoSuchOrder() {
        when(orderService.applyRefund(9999L, new BigDecimal("10.00"))).thenReturn(Optional.empty());

        IssueRefundTool tool = new IssueRefundTool(orderService, new DemoProperties(false, false));

        assertThatThrownBy(() -> tool.execute(new IssueRefundInput(9999L, new BigDecimal("10.00"), "reason", KEY)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("9999");
    }
}
