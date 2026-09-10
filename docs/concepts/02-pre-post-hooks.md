# Pre and post hooks

*Prerequisites: [Agentic loop](01-agentic-loop.md)*

## The problem

The model decides to refund a customer $5,000 on a $120 order. Maybe it
was persuaded, maybe it made an arithmetic mistake, maybe the prompt
just wasn't specific enough that day. Whatever the reason, nothing in the
system prompt can *guarantee* that request doesn't go through — a prompt
is a suggestion the model reads, not a rule the system enforces.

## The mechanism

Every tool call passes through a fixed, ordered list of interceptors
before it runs, and another pass after. A pre-hook can refuse the call
outright — the tool never executes. A post-hook only observes what
happened; it can't undo a call that already ran, but it can record it and
count it.

The interceptors are plain code, not prompt text, so "was this refund
too large" is a boolean a `BigDecimal` comparison answers — not something
a language model has to decide correctly every single time.

## Walk the code

1. **`service/governance/ToolInterceptor.java`.** Two methods,
   `preInvoke` and `postInvoke`, both with no-op defaults — an
   interceptor that only cares about one phase doesn't implement the
   other. Read this first; everything else is an implementation of it.
2. **`service/tool/ToolRegistry.java`, the `invoke(String, String,
   String)` method.** This is where the chain actually runs: parse
   arguments, run every interceptor's `preInvoke`, execute the tool if
   nothing objected, run every interceptor's `postInvoke`. There is no
   other path to a tool — a governance check here applies to every
   caller, including a test calling `invoke` directly.
3. **`service/governance/PolicyInterceptor.java`, `preInvoke`.** The
   guardrail the project exists to demonstrate: `amount` compared against
   `total - refundedAmount`, nothing more exotic than that.
4. **The `@Order` annotation on each interceptor class.** Allowlist (10)
   runs before Policy (20), which runs before Redaction (30), before
   Audit (40). Redaction has to run before Audit or there's nothing
   redacted yet for Audit to persist — see its class Javadoc.

## Code

```java
// PolicyInterceptor.java
@Override
public void preInvoke(ToolCallContext ctx) {
    if (!(ctx.input() instanceof IssueRefundInput refundRequest)) {
        return; // only issueRefund has a refund policy to enforce
    }
    OrderDto order = orderService.findById(refundRequest.orderId())
            .orElseThrow(() -> new PolicyViolationException("Order " + refundRequest.orderId() + " does not exist."));

    BigDecimal remaining = order.total().subtract(order.refundedAmount());
    if (refundRequest.amount().compareTo(remaining) > 0) {
        throw new PolicyViolationException(
                "Refund of $" + refundRequest.amount() + " exceeds the $" + remaining + " remaining...");
    }
}
```

Throwing `PolicyViolationException` here stops the chain immediately.
`ToolRegistry.invoke` catches it and returns a `ToolBlocked` result to
the *model* — not an exception to the caller — so the model can read the
reason and explain the refusal to the customer.

## Try changing it

Run `GuardrailPolicyTest#blocksARefundThatExceedsTheOrderTotal` and watch
it pass, then comment out the `if (refundRequest.amount()...)` block in
`PolicyInterceptor` and run it again. The test fails — a $5,000 refund on
a $120 order goes through — which is exactly the failure mode this
guardrail exists to prevent. Put the check back before moving on.

## What it does NOT solve

A pre-hook can only judge what's mechanically checkable — is this amount
larger than that one. It has no way to know whether a refund *within* the
cap is actually justified; a $50 refund for a made-up reason on a $120
order sails through this check just as easily as a legitimate one. Policy
enforcement bounds the blast radius of a bad decision; it doesn't replace
judging whether the decision was good.

## Related

- [Circuit breaker](03-circuit-breaker.md) — the post-hook that counts
  failures instead of blocking them
- [Prompt injection](05-prompt-injection.md) — why this same mechanism
  holds even when the model is actively being misled
- [Schema-forced output](04-schema-forced-output.md) — the other place
  this project puts structure ahead of trusting free text

## See it run

```bash
mvn test -Dtest=GuardrailPolicyTest
```

Expected: 5 tests pass — an over-cap refund blocked, an already-fully-
refunded order blocked, a nonexistent order blocked, a within-balance
refund allowed, and a non-refund tool call left untouched.
