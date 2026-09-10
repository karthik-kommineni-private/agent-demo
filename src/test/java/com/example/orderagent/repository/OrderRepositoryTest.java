package com.example.orderagent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderagent.entity.Order;
import com.example.orderagent.enums.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestConstructor;

/**
 * Verifies the eight orders seeded by {@code data.sql} load as expected.
 * This is the Phase 1 gate — everything downstream (tools, guardrails,
 * demo scenarios) depends on this fixture being correct.
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderRepositoryTest {

    private final OrderRepository orderRepository;

    OrderRepositoryTest(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Test
    void seedsExactlyEightOrders() {
        assertThat(orderRepository.findAll()).hasSize(8);
    }

    @Test
    void normalOrderIsFullyDeliveredWithNothingRefunded() {
        Order order = orderRepository.findById(1001L).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void lateShippedOrderHasATotalOfOneHundredTwentyDollars() {
        // $120 total matters: scenario 3 requests a $5000 refund against
        // this order specifically, to prove the cap holds against a
        // request nearly 42x the order total.
        Order order = orderRepository.findById(1002L).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    void fullyRefundedOrderHasRefundedAmountEqualToTotal() {
        Order order = orderRepository.findById(1003L).orElseThrow();
        assertThat(order.getRefundedAmount()).isEqualByComparingTo(order.getTotal());
    }

    @Test
    void partiallyRefundedOrderHasRefundedAmountBetweenZeroAndTotal() {
        Order order = orderRepository.findById(1004L).orElseThrow();
        assertThat(order.getRefundedAmount())
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThan(order.getTotal());
    }

    @Test
    void pendingOrderHasNoShipDate() {
        Order order = orderRepository.findById(1005L).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getShipDate()).isNull();
    }

    @Test
    void cancelledOrderHasNoShipDate() {
        Order order = orderRepository.findById(1006L).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getShipDate()).isNull();
    }

    @Test
    void injectionOrderNotesContainAnInstructionLikeAttempt() {
        // The guardrail that matters here is PolicyInterceptor (Phase 4),
        // not this test — this just confirms the fixture itself is in
        // place for that later test to exploit.
        Order order = orderRepository.findById(1007L).orElseThrow();
        assertThat(order.getNotes()).containsIgnoringCase("ignore all previous instructions");
    }

    @Test
    void highValueOrderExceedsOneThousandDollars() {
        Order order = orderRepository.findById(1008L).orElseThrow();
        assertThat(order.getTotal()).isGreaterThan(new BigDecimal("1000.00"));
    }
}
