package com.example.orderagent.service.governance;

import com.example.orderagent.service.AuditService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row for every tool call, and counts consecutive failures
 * for {@link BreakerRegistry} while it's here.
 *
 * <p>The post-tool hook already runs after every execution — success or
 * failure — so it's the natural place to observe outcomes. We didn't add a
 * separate hook just for failure counting; auditing and breaker counting
 * are both "record what just happened," and they happen to need the same
 * information at the same moment.
 *
 * <p>Runs last ({@code @Order(40)}), after {@link RedactionInterceptor}
 * ({@code @Order(30)}) has had a chance to put a redacted copy of the
 * arguments and result into {@link ToolCallContext#attributes()} — this
 * class only ever persists that redacted copy, never the raw one.
 */
@Component
@Order(40)
public class AuditInterceptor implements ToolInterceptor {

    private final AuditService auditService;
    private final BreakerRegistry breakerRegistry;

    public AuditInterceptor(AuditService auditService, BreakerRegistry breakerRegistry) {
        this.auditService = auditService;
        this.breakerRegistry = breakerRegistry;
    }

    @Override
    public void postInvoke(ToolCallContext ctx, ToolOutcome outcome) {
        // A policy block isn't a signal the tool itself is unhealthy, so it
        // doesn't touch the breaker either way — only a real execution
        // outcome does. See BreakerRegistry.recordSuccess for why a success
        // resets the count instead of decrementing it.
        switch (outcome) {
            case ToolOutcome.Success ignored -> breakerRegistry.recordSuccess(ctx.toolName());
            case ToolOutcome.Failure ignored -> breakerRegistry.recordFailure(ctx.toolName());
            case ToolOutcome.Blocked ignored -> {
                // no breaker effect
            }
        }

        boolean success = outcome instanceof ToolOutcome.Success;
        String redactedInput = (String) ctx.attributes().get("redactedInput");
        String redactedResult = (String) ctx.attributes().get("redactedResult");
        auditService.record(ctx.traceId(), ctx.toolName(), redactedInput, redactedResult, success);
    }
}
