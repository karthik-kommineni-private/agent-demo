package com.example.orderagent.entity;

import com.example.orderagent.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A customer order, as seen by the fake order system this demo runs
 * against.
 *
 * <p>Seeded entirely from {@code data.sql} — there is no order-creation
 * flow. This is the one entity in the project, kept deliberately narrow:
 * eight rows are enough to exercise every guardrail without needing a real
 * fulfillment system behind it.
 *
 * <p><b>Never returned from a controller.</b> Read through
 * {@link com.example.orderagent.service.OrderService}, which maps this to
 * {@link com.example.orderagent.dto.response.OrderDto} before it leaves the
 * service layer.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Null for an order that has not shipped yet (status PENDING or CANCELLED).
    @Column(name = "ship_date")
    private LocalDate shipDate;

    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount;

    @Column(length = 1000)
    private String notes;

    protected Order() {
        // required by JPA
    }

    public Order(
            Long id,
            String customerId,
            String customerEmail,
            BigDecimal total,
            OrderStatus status,
            LocalDate shipDate,
            BigDecimal refundedAmount,
            String notes) {
        this.id = id;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.total = total;
        this.status = status;
        this.shipDate = shipDate;
        this.refundedAmount = refundedAmount;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDate getShipDate() {
        return shipDate;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }
}
