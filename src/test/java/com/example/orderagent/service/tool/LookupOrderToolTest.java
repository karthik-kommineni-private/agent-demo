package com.example.orderagent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.dto.tool.LookupOrderInput;
import com.example.orderagent.dto.tool.LookupOrderOutput;
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
class LookupOrderToolTest {

    @Mock
    private OrderService orderService;

    @Test
    void returnsTheOrderWhenItExists() {
        OrderDto order = new OrderDto(
                1002L, "CUST-1002", "bilal@example.com", new BigDecimal("120.00"),
                OrderStatus.SHIPPED, LocalDate.of(2026, 8, 25), BigDecimal.ZERO, "shipped late");
        when(orderService.findById(1002L)).thenReturn(Optional.of(order));

        LookupOrderTool tool = new LookupOrderTool(orderService);
        LookupOrderOutput output = tool.execute(new LookupOrderInput(1002L));

        assertThat(output.orderId()).isEqualTo(1002L);
        assertThat(output.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(output.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void throwsWhenNoSuchOrder() {
        when(orderService.findById(9999L)).thenReturn(Optional.empty());

        LookupOrderTool tool = new LookupOrderTool(orderService);

        assertThatThrownBy(() -> tool.execute(new LookupOrderInput(9999L)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("9999");
    }
}
