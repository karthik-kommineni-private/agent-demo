package com.example.orderagent.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of {@code POST /orders/agent} — a customer request in plain
 * English, e.g. "Where is order 1002?" or "Refund order 1002 if it shipped
 * late."
 */
public record AgentRequest(@NotBlank String request) {
}
