package com.example.orderagent.enums;

/**
 * Where an order sits in its fulfillment lifecycle.
 *
 * <p>Refund state is tracked separately, on {@code Order.refundedAmount}.
 * A {@code DELIVERED} order with {@code refundedAmount == total} is a fully
 * refunded order — there is no separate {@code REFUNDED} status, because
 * fulfillment and refund are independent facts about an order.
 */
public enum OrderStatus {
    PENDING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
