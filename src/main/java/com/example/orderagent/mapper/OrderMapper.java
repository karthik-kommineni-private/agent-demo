package com.example.orderagent.mapper;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.entity.Order;
import org.springframework.stereotype.Component;

/**
 * Converts between the {@link Order} entity and {@link OrderDto}.
 *
 * <p>Hand-written rather than generated: two fields wide either way, and a
 * mapping library is one more thing to explain in a project about legible
 * control flow.
 */
@Component
public class OrderMapper {

    public OrderDto toDto(Order order) {
        return new OrderDto(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getTotal(),
                order.getStatus(),
                order.getShipDate(),
                order.getRefundedAmount(),
                order.getNotes());
    }
}
