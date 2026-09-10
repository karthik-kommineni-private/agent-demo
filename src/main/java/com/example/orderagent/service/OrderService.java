package com.example.orderagent.service;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.entity.Order;
import com.example.orderagent.mapper.OrderMapper;
import com.example.orderagent.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain domain operations on orders. No AI concerns live here — this class
 * would be identical in a project with no agent in it at all.
 *
 * <p>Refund policy (caps, already-refunded checks, allowlisting) is
 * deliberately not enforced here. That lives in
 * {@code service.governance.PolicyInterceptor}, which runs before a tool
 * reaches this class. This class trusts its caller and just persists the
 * write, the same way a repository trusts the service above it.
 *
 * @see com.example.orderagent.service.governance.PolicyInterceptor
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    /**
     * Looks up an order by id.
     *
     * @param orderId the order id
     * @return the order, or empty if no such order exists
     */
    public Optional<OrderDto> findById(Long orderId) {
        return orderRepository.findById(orderId).map(orderMapper::toDto);
    }

    /**
     * Records a refund against an order by adding to its refunded amount.
     *
     * <p>Callers are expected to have already validated the amount (refund
     * caps, already-refunded checks) through the governance layer before
     * calling this — see the class-level note above.
     *
     * @param orderId the order id
     * @param amount  the amount to add to the order's refunded total
     * @return the updated order, or empty if no such order exists
     */
    @Transactional
    public Optional<OrderDto> applyRefund(Long orderId, BigDecimal amount) {
        return orderRepository.findById(orderId).map(order -> {
            order.setRefundedAmount(order.getRefundedAmount().add(amount));
            Order saved = orderRepository.save(order);
            return orderMapper.toDto(saved);
        });
    }
}
