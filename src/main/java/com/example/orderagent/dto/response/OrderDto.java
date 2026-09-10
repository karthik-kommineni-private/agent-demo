package com.example.orderagent.dto.response;

import com.example.orderagent.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The order shape returned to callers outside the service layer — the tool
 * layer, and ultimately the model. Never carries the JPA entity itself.
 */
public record OrderDto(
        Long id,
        String customerId,
        String customerEmail,
        BigDecimal total,
        OrderStatus status,
        LocalDate shipDate,
        BigDecimal refundedAmount,
        String notes) {
}
