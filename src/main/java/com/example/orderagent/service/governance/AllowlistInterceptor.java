package com.example.orderagent.service.governance;

import com.example.orderagent.config.DemoProperties;
import com.example.orderagent.exception.PolicyViolationException;
import com.example.orderagent.service.tool.OrderAgentTool;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The first gate every tool call passes through: is this tool one we
 * actually meant to expose, and — for the demo's dry-run mode — is it a
 * write operation we're currently refusing to let touch real data?
 *
 * <p>Checking "is this tool registered" here is redundant with
 * {@code ToolRegistry.get} today, since nothing can call a tool that isn't
 * registered in the first place. It earns its place anyway: it's the
 * single spot that would need to change if tools were ever resolved some
 * other way (loaded from config, discovered reflectively), and it's where
 * dry-run mode belongs — "which tools may actually run" is exactly an
 * allowlist's job.
 *
 * <p>Runs first ({@code @Order(10)}) so a disallowed call never reaches
 * policy or execution at all.
 */
@Component
@Order(10)
public class AllowlistInterceptor implements ToolInterceptor {

    private final Map<String, OrderAgentTool<Object, Object>> allowedTools;
    private final DemoProperties demoProperties;

    @SuppressWarnings("unchecked")
    public AllowlistInterceptor(List<OrderAgentTool<?, ?>> tools, DemoProperties demoProperties) {
        this.allowedTools = tools.stream()
                .map(tool -> (OrderAgentTool<Object, Object>) tool)
                .collect(Collectors.toMap(OrderAgentTool::name, Function.identity()));
        this.demoProperties = demoProperties;
    }

    @Override
    public void preInvoke(ToolCallContext ctx) {
        OrderAgentTool<Object, Object> tool = allowedTools.get(ctx.toolName());
        if (tool == null) {
            throw new PolicyViolationException("Tool \"" + ctx.toolName() + "\" is not on the allowlist.");
        }
        if (demoProperties.dryRunMode() && tool.isWriteOperation()) {
            throw new PolicyViolationException(
                    "Dry-run mode is enabled for this demo; \"" + ctx.toolName() + "\" was not executed.");
        }
    }
}
