# Circuit breaker

*Prerequisites: [Agentic loop](01-agentic-loop.md), [Pre and post hooks](02-pre-post-hooks.md)*

## The problem

The refund API goes down. A customer asks for a refund. The model calls
`issueRefund`, gets a failure, sees the error, and — per this project's
own system prompt — reasonably retries. It fails again. It retries a
second time, then a third.

Nothing here is a bug. Retrying a transient failure is exactly what a
sensible person would do, and the system prompt (`prompts/order-agent-
system-prompt.md`, rule 4) tells the model to do exactly that. But the
model has no memory of how long this has been going on and no sense of
when to give up. Left alone, it will burn the iteration cap on a
dependency that isn't coming back, and it will do this for every request
that arrives while the API is down.

The failure mode isn't that the agent breaks. It's that the agent keeps
working, expensively, on something that cannot succeed.

## The mechanism

Count consecutive failures of the same tool. When the count crosses a
threshold, stop trying and escalate instead.

Three details make it work:

**Consecutive, not total.** A tool that fails once an hour is flaky, not
broken. One success resets the counter to zero. Without this, a healthy
tool slowly trips the breaker over a long-running process.

**Keyed per tool, not global.** `issueRefund` being down shouldn't stop
`lookupOrder` from working. Each tool gets its own counter.

**Escalation, not an exception thrown at the caller.** When the breaker
opens, the loop doesn't crash. It exits cleanly and returns a normal
`AgentResponse` with status `ESCALATED`, so a human picks up the request
with the full trace of what was attempted.

The name comes from electrical circuit breakers, and the analogy holds:
it trips to prevent damage, and something has to decide when to close it
again — in this project, nothing does; see "What it does NOT solve"
below.

## Walk the code

1. **`service/governance/BreakerRegistry.java`.** The counter itself.
   Start here. Note that it's keyed by tool name and that
   `recordSuccess` removes the entry rather than decrementing it. Ask
   yourself why decrementing would be wrong — the answer is in the
   comment above that line.

2. **`service/governance/AuditInterceptor.java`, `postInvoke`.** Where
   failures get counted. The post-tool hook already runs after every
   execution — see `02-pre-post-hooks.md` — so it's the natural place to
   observe outcomes. There's no separate hook just for failure counting;
   auditing and breaker counting are both "record what just happened,"
   and they need the same information at the same moment.

3. **`service/agent/AgentLoop.java`, the top of the `for` loop in
   `run()`.** The check happens *before* asking the model again, not
   after. This is the detail that matters: an open breaker costs zero
   tokens rather than one more round trip, because the loop never places
   the call.

4. **`enums/TerminationReason.java`.** `BREAKER_OPEN` sits alongside
   `COMPLETED` as a first-class outcome, not an error path bolted on
   afterward.

5. **`config/AgentProperties.java`, `breakerThreshold`.** The threshold
   comes from `application.yml` (`orderagent.breaker-threshold`, default
   3). Changing the policy is a config change, not a deploy.

## Code

```java
// BreakerRegistry.java
public void assertClosed(String toolName) {
    if (isOpen(toolName)) {
        throw new BreakerOpenException(toolName);
    }
}

public boolean isOpen(String toolName) {
    return consecutiveFailures.getOrDefault(toolName, 0) >= threshold;
}

public void recordFailure(String toolName) {
    consecutiveFailures.merge(toolName, 1, Integer::sum);
}

public void recordSuccess(String toolName) {
    // Reset, not decrement. One success means the tool is healthy right
    // now, and that's what we care about. Decrementing would let a tool
    // that fails 2 out of every 3 calls stay closed forever.
    consecutiveFailures.remove(toolName);
}
```

```java
// AgentLoop.java — checked before each model call, not after
if (lastToolName != null) {
    try {
        breakerRegistry.assertClosed(lastToolName);
    } catch (BreakerOpenException e) {
        return AgentResponse.escalated(traceBuilder.build(), iteration - 1, totalTokens, totalCost,
                TerminationReason.BREAKER_OPEN, modelUsed);
    }
}
```

## Try changing it

Set `orderagent.breaker-threshold: 1` in `application.yml`, turn on fault
injection with `orderagent.demo.fail-refund-tool=true`, and ask for a
refund. The agent now escalates after a single failed attempt instead of
three. Run it live and check `trace.steps` in the response — with a
threshold of 1 there's exactly one `issueRefund` attempt before
`BREAKER_OPEN`; with the default of 3 there are three, because the system
prompt tells the model to retry a technical failure with the same
idempotency key before giving up.

## What it does NOT solve

**It doesn't distinguish a broken tool from a wrong request.** If the
model kept calling `issueRefund` with an order id that doesn't exist,
`PolicyInterceptor` would block every one of those calls as a policy
violation, not a failure — see `02-pre-post-hooks.md` for why a block and
a failure are counted differently. But if a bug elsewhere caused
`issueRefund` to genuinely throw on valid input, the breaker opens on
that just the same, and tells you something is failing repeatedly, not
why.

**It doesn't recover.** There is no half-open state here — nothing tests
whether the tool has come back once the breaker trips. Once open, it
stays open for the process's lifetime. Resilience4j's `CircuitBreaker`
supports half-open transitions and a real deployment should use it
instead of this hand-rolled counter; this project keeps it simple because
the pattern is the point, not the library.

**It's per-process.** Run two instances and each has its own counter with
no shared state. `docs/production-readiness.md` covers what a shared,
distributed version of this would need.

**It doesn't stop bad decisions, only repeated failures.** An agent that
successfully issues one wrong refund never trips anything — that's what
[pre-hooks](02-pre-post-hooks.md) are for.

## Related

- [Pre and post hooks](02-pre-post-hooks.md) — the hook this counting
  logic actually lives inside
- [Agentic loop](01-agentic-loop.md) — the loop this breaker check
  short-circuits
- [Determinism](06-determinism.md) — why the golden test for this
  scenario doesn't depend on a real API being down

## See it run

```bash
mvn test -Dtest=CircuitBreakerTest
```

Expected: `threeConsecutiveFailuresOpenTheBreakerForThatTool` shows the
breaker closed after one and two failures and open after the third, and
`oneSuccessResetsTheFailureCount` shows two prior failures wiped out by a
single success. Both run against the real `ToolRegistry` and
`AuditInterceptor` — only `OrderService` and `AuditService` are mocked.

To see it end to end against a live model:

```bash
export ANTHROPIC_API_KEY=sk-...
./mvnw spring-boot:run -Dspring-boot.run.arguments="--orderagent.demo.fail-refund-tool=true" &
curl -X POST localhost:8080/orders/agent -H 'Content-Type: application/json' \
  -d '{"request": "Please refund order 1005 for $10, reason: damaged."}'
```

Expected: three `issueRefund` attempts in `trace.steps` with the same
`idempotencyKey`, then `"status":"ESCALATED"` and
`"terminationReason":"BREAKER_OPEN"`.
