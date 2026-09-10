package com.example.orderagent.service.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orderagent.config.DemoProperties;
import com.example.orderagent.dto.tool.IssueRefundInput;
import com.example.orderagent.dto.tool.LookupOrderInput;
import com.example.orderagent.exception.PolicyViolationException;
import com.example.orderagent.service.tool.IssueRefundTool;
import com.example.orderagent.service.tool.LookupOrderTool;
import com.example.orderagent.service.tool.OrderAgentTool;
import com.example.orderagent.service.tool.SubmitAnswerTool;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AllowlistInterceptorTest {

    private List<OrderAgentTool<?, ?>> tools() {
        return List.of(
                new LookupOrderTool(Mockito.mock(com.example.orderagent.service.OrderService.class)),
                new IssueRefundTool(
                        Mockito.mock(com.example.orderagent.service.OrderService.class), new DemoProperties(false, false)),
                new SubmitAnswerTool());
    }

    @Test
    void blocksAToolNameThatIsNotRegistered() {
        AllowlistInterceptor interceptor = new AllowlistInterceptor(tools(), new DemoProperties(false, false));
        ToolCallContext ctx = new ToolCallContext("trace-1", "deleteOrder", null);

        assertThatThrownBy(() -> interceptor.preInvoke(ctx))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void blocksAWriteToolInDryRunMode() {
        AllowlistInterceptor interceptor = new AllowlistInterceptor(tools(), new DemoProperties(false, true));
        ToolCallContext ctx = new ToolCallContext(
                "trace-1", "issueRefund", new IssueRefundInput(1002L, new BigDecimal("10.00"), "r", "key-00000001"));

        assertThatThrownBy(() -> interceptor.preInvoke(ctx))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("Dry-run");
    }

    @Test
    void allowsAReadOnlyToolInDryRunMode() {
        AllowlistInterceptor interceptor = new AllowlistInterceptor(tools(), new DemoProperties(false, true));
        ToolCallContext ctx = new ToolCallContext("trace-1", "lookupOrder", new LookupOrderInput(1002L));

        interceptor.preInvoke(ctx); // does not throw
    }
}
