package com.example.orderagent.dto.tool;

/**
 * Arguments for {@code lookupOrder}. This is the source of the JSON Schema
 * sent to the model — see {@code LookupOrderTool.inputSchema()}.
 */
public record LookupOrderInput(Long orderId) {
}
