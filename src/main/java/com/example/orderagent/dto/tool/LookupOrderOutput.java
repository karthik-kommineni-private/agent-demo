package com.example.orderagent.dto.tool;

import com.example.orderagent.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of {@code lookupOrder}, serialized to JSON and handed back to the
 * model.
 */
public record LookupOrderOutput(
        Long orderId,
        String customerId,
        String customerEmail,
        BigDecimal total,
        OrderStatus status,
        LocalDate shipDate,
        BigDecimal refundedAmount,
        String notes) {
}
