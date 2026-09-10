package com.example.orderagent.service.tool;

import com.example.orderagent.dto.response.OrderDto;
import com.example.orderagent.dto.tool.LookupOrderInput;
import com.example.orderagent.dto.tool.LookupOrderOutput;
import com.example.orderagent.exception.ToolExecutionException;
import com.example.orderagent.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * Read-only lookup of a single order by id.
 *
 * <p>The only failure mode is "no such order." There is nothing else to
 * guard here, which is why {@code issueRefund} carries a policy check in
 * Phase 4 and this tool does not — a lookup can't hurt anyone.
 */
@Component
public class LookupOrderTool implements OrderAgentTool<LookupOrderInput, LookupOrderOutput> {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "orderId": {
                  "type": "integer",
                  "description": "The numeric order id to look up."
                }
              },
              "required": ["orderId"],
              "additionalProperties": false
            }
            """;

    private final OrderService orderService;

    public LookupOrderTool(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String name() {
        return "lookupOrder";
    }

    @Override
    public String description() {
        return "Looks up a single order by id and returns its status, total, ship date and refund history.";
    }

    @Override
    public String inputSchema() {
        return INPUT_SCHEMA;
    }

    @Override
    public Class<LookupOrderInput> inputType() {
        return LookupOrderInput.class;
    }

    @Override
    public LookupOrderOutput execute(LookupOrderInput input) {
        OrderDto order = orderService
                .findById(input.orderId())
                .orElseThrow(() -> new ToolExecutionException(name(), "No order found with id " + input.orderId()));
        return new LookupOrderOutput(
                order.id(),
                order.customerId(),
                order.customerEmail(),
                order.total(),
                order.status(),
                order.shipDate(),
                order.refundedAmount(),
                order.notes());
    }
}
