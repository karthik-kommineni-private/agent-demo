package com.example.orderagent.service.governance;

import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Strips PII out of a tool call's arguments and result before anything
 * downstream — right now, only {@link AuditInterceptor} — gets to see them.
 *
 * <p>This does not touch what the model sees. The tool's real output still
 * goes back to the model with a customer's email intact, because the model
 * needs it to do its job. What this protects is the audit trail: non-
 * negotiable rule 9 says no secrets in logs, traces, or audit rows, and an
 * email address is exactly the kind of thing that rule is about.
 *
 * <p>Runs after policy checks but before {@link AuditInterceptor}
 * ({@code @Order(30)}, versus Audit's {@code @Order(40)}) — audit has
 * nothing to redact if this hasn't put a redacted copy into
 * {@link ToolCallContext#attributes()} first.
 *
 * <p><b>Why it works on raw JSON, not typed fields:</b> a regex over the
 * serialized form catches an email address wherever it appears — in
 * {@code LookupOrderOutput.customerEmail} today, in some other field
 * tomorrow — without this class needing to know every DTO shape that might
 * carry one.
 */
@Component
@Order(30)
public class RedactionInterceptor implements ToolInterceptor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final String REDACTED_EMAIL = "[redacted-email]";

    private final ObjectMapper objectMapper;

    public RedactionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void postInvoke(ToolCallContext ctx, ToolOutcome outcome) {
        ctx.attributes().put("redactedInput", redact(writeQuietly(ctx.input())));
        String rawResult = switch (outcome) {
            case ToolOutcome.Success success -> writeQuietly(success.output());
            case ToolOutcome.Failure failure -> failure.exception().getMessage();
            case ToolOutcome.Blocked blocked -> blocked.reason();
        };
        ctx.attributes().put("redactedResult", redact(rawResult));
    }

    private String redact(String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll(REDACTED_EMAIL);
    }

    private String writeQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            // Redaction must never be the reason a tool call fails. Losing
            // the audit detail for this one call is safer than throwing
            // out of a post-hook and masking the tool's real outcome.
            return "[unserializable]";
        }
    }
}
