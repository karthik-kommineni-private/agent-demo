package com.example.orderagent.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderagent.dto.tool.LookupOrderOutput;
import com.example.orderagent.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RedactionInterceptorTest {

    @Test
    void redactsAnEmailAddressOutOfASuccessfulResult() {
        RedactionInterceptor interceptor = new RedactionInterceptor(JsonMapper.builder().build());
        LookupOrderOutput output = new LookupOrderOutput(
                1001L, "CUST-1001", "alice@example.com", new BigDecimal("89.99"),
                OrderStatus.DELIVERED, LocalDate.of(2026, 8, 14), BigDecimal.ZERO, "on time");
        ToolCallContext ctx = new ToolCallContext("trace-1", "lookupOrder", null);

        interceptor.postInvoke(ctx, new ToolOutcome.Success(output));

        String redacted = (String) ctx.attributes().get("redactedResult");
        assertThat(redacted).doesNotContain("alice@example.com").contains("[redacted-email]");
    }

    @Test
    void passesThroughAFailureMessageWithNoEmailUnchanged() {
        RedactionInterceptor interceptor = new RedactionInterceptor(JsonMapper.builder().build());
        ToolCallContext ctx = new ToolCallContext("trace-1", "issueRefund", null);

        interceptor.postInvoke(ctx, new ToolOutcome.Failure(new RuntimeException("no such order")));

        assertThat(ctx.attributes().get("redactedResult")).isEqualTo("no such order");
    }
}
