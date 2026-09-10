package com.example.orderagent.repository;

import com.example.orderagent.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the seeded {@code orders} table.
 *
 * <p>No custom queries — the two tools this project has ({@code lookupOrder}
 * and {@code issueRefund}) only ever need a single order by id.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
