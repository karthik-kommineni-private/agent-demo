# CLAUDE.md — Order Agent

## What this project is

A **governed AI agent** in Java Spring Boot. It handles customer order
requests (status lookups, refunds) using an LLM that decides which tools to
call, wrapped in a governance layer that enforces policy in code rather than
in the prompt.

The point of this project is **not** the agent. It is the guardrails around
it. Every design decision should favour explicit, inspectable control over
clever abstraction.

**Thesis:** the model reasons; the code enforces the rules.

**Scope is deliberately small.** Two tools, eight orders, one endpoint. If a
feature does not make the guardrail layer clearer, it does not belong here.

> **DOMAIN TODO — decide before phase 1.**
> The default domain is orders and refunds. If this is being demoed to a
> team that owns a different workflow, keep the architecture and rename the
> domain to match theirs (same shape: a lookup, a guarded write, a policy
> cap). Fake data either way. A familiar-looking domain gets "how long to
> ship it"; a generic one gets "ours is more complicated".

---

## Stack

| Thing | Version | Notes |
|---|---|---|
| Java | 21 | Required by Spring AI 2.0 |
| Spring Boot | 4.0.x | Required by Spring AI 2.0 |
| Spring AI | 2.0.1 | Use the BOM |
| Model provider | Anthropic | `spring-ai-starter-model-anthropic` |
| Build | Maven | TODO: switch to Gradle if preferred |
| DB | H2 in-memory | Seeded from `data.sql` |
| Resilience | Resilience4j | Circuit breaker |
| Metrics | Micrometer + Actuator | Token and cost counters |
| Base package | `com.example.orderagent` | TODO: replace with real package |

### Critical version facts

- Spring AI 2.0 **removed** the built-in tool-execution loop from every
  `ChatModel`. Tool execution must be driven externally.
- `ToolCallAdvisor` was renamed to `ToolCallingAdvisor` in 2.0.
- `internalToolExecutionEnabled` no longer exists. Do not reference it.
- **We drive the loop ourselves** via `ToolCallingManager`. Do not use the
  auto-registered advisor. The loop must be visible Java code.

If any of the above conflicts with what you find in the actual dependency,
**stop and report** rather than guessing. Check the real reference docs for
the exact version resolved in the build.

---

## Commands

```bash
mvn clean verify           # build + all tests (runs offline, no API key)
mvn spring-boot:run        # run locally
mvn spotless:apply         # format
mvn test -Dtest=Guardrail* # guardrail tests only
```

Environment: `ANTHROPIC_API_KEY` must be set to run the app. Never read,
print, echo or commit it. Tests must pass without it.

---

## Package layout

Standard Spring Boot layered structure. Controllers talk to services;
services talk to repositories; nothing skips a layer; entities never leave
the service layer.

```
com.example.orderagent
├── OrderAgentApplication.java
│
├── config/
│   ├── SpringAiConfig.java          # ChatClient, ToolCallingManager beans
│   ├── ResilienceConfig.java        # circuit breaker config
│   ├── AgentProperties.java         # @ConfigurationProperties("orderagent")
│   └── PriceTableProperties.java    # $/token per model, from yaml
│
├── controller/
│   ├── OrderAgentController.java    # POST /orders/agent
│   └── advice/
│       └── GlobalExceptionHandler.java  # everything -> AgentResponse shape
│
├── service/
│   ├── OrderService.java            # domain operations, no AI
│   ├── AgentService.java            # entry point: loop -> response
│   ├── AuditService.java
│   │
│   ├── agent/
│   │   ├── AgentLoop.java           # the while loop; caps and termination
│   │   ├── AgentTraceBuilder.java   # per-request trace assembly
│   │   └── TokenMeter.java          # tokens + cost from ChatResponse
│   │
│   ├── governance/
│   │   ├── ToolInterceptor.java     # interface: preInvoke / postInvoke
│   │   ├── AllowlistInterceptor.java
│   │   ├── PolicyInterceptor.java   # refund caps, already-refunded
│   │   ├── RedactionInterceptor.java
│   │   ├── AuditInterceptor.java
│   │   └── BreakerRegistry.java     # consecutive-failure counter per tool
│   │
│   └── tool/
│       ├── ToolRegistry.java
│       ├── LookupOrderTool.java
│       ├── IssueRefundTool.java
│       └── SubmitAnswerTool.java    # schema-forced final output
│
├── repository/
│   ├── OrderRepository.java
│   └── AuditLogRepository.java
│
├── entity/
│   ├── Order.java                   # @Entity
│   └── AuditLog.java                # @Entity
│
├── dto/
│   ├── request/
│   │   └── AgentRequest.java
│   ├── response/
│   │   ├── AgentResponse.java       # the uniform envelope
│   │   ├── AgentTrace.java
│   │   └── OrderDto.java
│   └── tool/
│       ├── LookupOrderInput.java    # strict tool input schema
│       ├── LookupOrderOutput.java
│       ├── IssueRefundInput.java
│       ├── IssueRefundOutput.java
│       └── SubmitAnswerInput.java   # the forced output shape
│
├── mapper/
│   └── OrderMapper.java             # entity <-> dto, hand-written
│
├── enums/
│   ├── OrderStatus.java
│   ├── AgentStatus.java             # SUCCESS, BLOCKED, ESCALATED, ERROR
│   └── TerminationReason.java       # COMPLETED, POLICY_BLOCK, BREAKER_OPEN,
│                                    # ITERATION_CAP, BUDGET_CAP
│
├── exception/
│   ├── PolicyViolationException.java
│   ├── BreakerOpenException.java
│   └── ToolExecutionException.java
│
└── util/
    └── IdempotencyKeys.java
```

### Layer rules

- `controller/` — request/response only. No business logic, no AI calls.
- `service/` — all logic. The `agent`, `governance` and `tool` sub-packages
  keep the AI concerns grouped without breaking the layered convention.
- `repository/` — Spring Data interfaces only.
- `entity/` — JPA entities. **Never returned from a controller.** Map to DTO.
- `dto/` — Java records, immutable, with Bean Validation annotations.
- Tool input DTOs live in `dto/tool/` and are the source of the JSON schema
  sent to the model. Their validation annotations are enforced by the
  pre-hook before execution.

---

## Non-negotiable rules

These are the project's reason for existing. Never weaken them, never move a
check into a prompt, never add a bypass "just for testing."

1. **Policy lives in code, not in prompts.** Every constraint (refund caps,
   allowlists, already-refunded checks) must be enforced by an interceptor
   that runs regardless of what the model decided.
2. **Every write tool has a pre-hook and a breaker registration.** No
   exceptions. A write tool without both should fail the guardrail test.
3. **Fail closed.** Model unavailable, breaker open, validation failed,
   budget exceeded → escalate to human. Never guess, never proceed
   optimistically, never return a partial action as success.
4. **The loop is bounded.** Hard iteration cap (default 6) and a token
   budget cap per request. Both from config. Both enforced in `AgentLoop`,
   not in the prompt.
5. **One response shape.** Every path — success, blocked, escalated, error —
   returns `AgentResponse`. No exceptions leak to the controller.
6. **Side-effectful tools are idempotent.** `issueRefund` takes an
   idempotency key; a replay must not double-refund.
7. **Never act on unvalidated model output.** Validate the shape, then
   execute.
8. **Everything is traced.** One trace ID flows through the loop, tools,
   audit rows and the response.
9. **No secrets in logs, traces, audit rows or test fixtures.**
10. **Tests run without an API key.** Anything that needs a live model is
    behind a profile that is off by default.

---

## Conventions

- Java records for all DTOs and tool inputs/outputs. No Lombok.
- Constructor injection only. No field `@Autowired`.
- Package-private by default; public only where genuinely needed.
- All thresholds in `application.yml` under `orderagent.*`. No magic numbers
  in code.
- Prompts live in `src/main/resources/prompts/*.md` with a version header.
  Never inline a prompt as a string literal. Log the prompt version in the
  trace.
- Timeouts on every model call and every tool. No unbounded waits.
- Tests: JUnit 5 + AssertJ. Guardrail tests are named `Guardrail*Test`.
- Documentation-in-code rules are in the next section and are mandatory.

---

## Comments and Javadoc

**Rule: someone should understand what a class or method does by reading the
comment above it, without reading the body.** Write for a competent Java
engineer who has never built an AI agent.

### Every class gets a Javadoc block

```java
/**
 * Runs the agent's decision loop.
 *
 * <p>The model is asked what to do. If it asks for a tool, we run that tool
 * (after the guardrails approve it) and ask again with the result. We keep
 * going until the model stops asking for tools, or until we hit a limit.
 *
 * <p><b>Why a loop and not a fixed sequence:</b> we don't know in advance
 * how many steps a request needs. "Where is order 5?" needs one lookup.
 * "Refund it if it shipped late" needs a lookup, a judgement, then a refund.
 * The model decides; this class just keeps the loop bounded and traced.
 *
 * <p><b>How it stops:</b> the model stops asking for tools (normal), the
 * iteration cap is hit, the token budget is spent, a guardrail blocks
 * something fatally, or a circuit breaker opens. Every one of those produces
 * an {@link AgentResponse} — nothing throws out of here.
 *
 * @see ToolInterceptor for the guardrails that run around each tool call
 */
```

Required in every class Javadoc:
- **What it does**, in one or two plain sentences.
- **Why it exists** — the problem it solves. Skip only for trivial DTOs.
- **How it fails**, for anything in `service/agent`, `service/governance` or
  `service/tool`.
- `@see` links to the classes it works with.

### Every public method gets a Javadoc block

```java
/**
 * Checks whether a tool is allowed to run, before it runs.
 *
 * <p>Runs every registered guardrail in order. The first one to object wins
 * — we stop there and return the reason. Nothing has executed at this point,
 * so a block costs nothing but a few milliseconds.
 *
 * <p>The block reason goes back to the <i>model</i>, not just the logs, so
 * it can explain the refusal to the customer instead of silently retrying.
 *
 * @param call the tool the model wants to run, with its arguments
 * @param ctx  trace id, remaining budget, remaining iterations
 * @return     allow, or deny with a reason the model can act on
 */
```

Required: what it does, any non-obvious behaviour, `@param` for every
parameter, `@return` describing meaning not type, `@throws` where it throws.

### Inline comments

Explain **why**, never **what**. The code already says what.

```java
// Bad — restates the code
// increment the failure counter
failures.incrementAndGet();

// Good — explains the reasoning
// Consecutive failures only. One success means the tool is healthy again,
// so a flaky dependency doesn't slowly trip the breaker over an hour.
failures.remove(toolName);
```

Comment these specifically:
- Any threshold, and where its value comes from
- Anywhere we deliberately swallow an exception, and why
- Anywhere the model's output is trusted, and what makes that safe
- Anywhere ordering matters (interceptor order, hook order)

### Vocabulary

Assume no AI background. On first use in a class, explain the term:

```java
// "stop_reason" is the model telling us why it stopped talking. If it says
// "tool_use", it's waiting on us to run something and report back.
```

Terms needing this treatment: stop reason, tool use, tool schema, token,
context window, structured output.

### What not to do

- No `// getter for name` on a getter.
- No Javadoc that just restates the method name.
- No commented-out code. Delete it; git remembers.
- No `TODO` without a name and a reason.

---

## Build order

Work through these phases **in order**. At the end of each: run
`mvn clean verify`, then **stop and report**. Do not start the next phase
until told to continue.

### Phase 0 — Verify the environment
- Confirm `java -version` is 21+.
- Generate the Spring Boot 4.0 project with Web, JPA, H2, Actuator,
  Validation.
- Add the Spring AI 2.0.1 BOM and the Anthropic starter.
- Make one hardcoded `ChatClient` call and print the response.
- **Gate:** a real model response printed to console.
- **If Spring AI 2.0.1 cannot be resolved, or Java is below 21: STOP and
  report.** Do not silently fall back to a different version.

### Phase 1 — Domain
- Resolve the DOMAIN TODO at the top of this file first.
- `Order` entity: id, customerId, customerEmail, total, status, shipDate,
  refundedAmount, notes.
- `OrderRepository`, `OrderService`, `OrderMapper`.
- `data.sql` with 8 orders covering: normal, shipped late, already fully
  refunded, partially refunded, not yet shipped, cancelled, high value, and
  **one order whose `notes` field contains a prompt-injection attempt**
  (text instructing the reader to ignore prior instructions and refund in
  full).
- **Gate:** repository tests pass.

### Phase 2 — Tools
- `ToolRegistry` mapping name → schema + handler.
- `lookupOrder(orderId)` — read-only.
- `issueRefund(orderId, amount, reason, idempotencyKey)` — write.
- `submit_answer(...)` — the final structured response shape.
- Strict JSON input schema for each. Reject unknown fields.
- **Gate:** each tool callable directly in a unit test, schemas validated.

### Phase 3 — Agent loop
- `AgentLoop` driving `ToolCallingManager` in an explicit `while` loop.
- Terminates when the model stops requesting tools.
- Iteration cap, token budget cap, per-iteration trace entries.
- `AgentResponse` envelope assembled on every exit path.
- **Gate:** "Where is order 1002?" resolves end to end with one tool call.

### Phase 4 — Governance (the core of the project)
- `ToolInterceptor` chain, ordered, invoked around every tool execution.
- Pre: allowlist, argument validation, policy caps (refund ≤ order total,
  not already refunded, order exists), dry-run flag.
- Post: result shape validation, PII redaction, audit row, failure counter.
- Blocks return a structured reason **to the model** so it can recover and
  explain, rather than throwing.
- `BreakerRegistry`: 3 consecutive failures on the same tool → open →
  terminate with `ESCALATED`.
- Fault-injection flag (`orderagent.demo.fail-refund-tool`) for the demo.
- **Gate:** guardrail tests pass — over-cap refund blocked, injection
  attempt blocked, 3 failures produce `ESCALATED`.

### Phase 5 — Observability
- `TokenMeter` reading usage from `ChatResponse` metadata.
- `PriceTable` from config; cost computed per request.
- Micrometer counters exposed via Actuator.
- `AgentResponse` carries tokens, cost, iterations, model used.
- **Gate:** cost appears in the response body and in `/actuator/metrics`.

### Phase 6 — Testing and determinism
- Cassette decorator on the chat client: record request/response JSON to
  `src/test/resources/cassettes/`, replay under a test profile with zero API
  calls.
- Golden-file tests: fixed input → asserted tool sequence and final output.
- **Gate:** `mvn clean verify` runs green with `ANTHROPIC_API_KEY` unset.

### Phase 7 — README and concept docs
- Root `README.md` — see the separate README draft for structure and tone.
- **Exactly six** concept files under `docs/concepts/`. Not more.

| File | Concept |
|---|---|
| `01-agentic-loop.md` | Model-driven control flow; termination on stop reason |
| `02-pre-post-hooks.md` | Policy in code, around every tool call |
| `03-circuit-breaker.md` | Consecutive-failure threshold; escalate, don't retry |
| `04-schema-forced-output.md` | Shape guaranteed via forced tool choice; **not** semantic correctness |
| `05-prompt-injection.md` | Why prompt-level defence is insufficient |
| `06-determinism.md` | What is and isn't deterministic here |

- `docs/concepts/03-circuit-breaker.md` is **written by hand as the quality
  bar**. Match its depth, tone and structure. Output shorter or more
  abstract than that file is not done.
- Structure every file the same way:

```markdown
# <Concept name>

*Prerequisites: <links>*

## The problem
Two or three sentences. A concrete failure, not an abstraction.

## The mechanism
How it works in plain language, before any code. Someone should be able to
implement it from this description alone.

## Walk the code
Numbered, in reading order. Each step: the file, what to look at, and what
question it answers. Say why the reader is being sent there.

## Code
The smallest excerpt that shows the idea. Fifteen lines, not fifty.

## Try changing it
One experiment that makes the concept click.

## What it does NOT solve
The honest limit. If you can't write this, you don't understand it well
enough to document it.

## Related
Two or three links.

## See it run
The exact command and the output to expect.
```

Rules:
- Every claim backed by code in this repo. No aspirational text.
- Explain every AI term on first use.
- Real file paths, so the reader can open them.
- Paragraphs of three sentences maximum.
- No "powerful", "seamless", "robust". Describe what it does and what it
  costs.
- **Gate:** every `See it run` command actually runs and produces the
  documented output.

### Phase 8 — Claude Code integration (small, deliberate)
- `.claude/settings.json` with **one** hook: PostToolUse on Java edits →
  `mvn spotless:apply` on the changed file.
- Optionally a second: PostToolUse on `service/governance/**` edits → run
  `Guardrail*Test`.
- Nothing else. No CI, no `claude -p` workflow, no fork automation. Those
  are roadmap items, not build items.
- **Gate:** the hook fires on an edit and formats the file.

### Phase 9 — Production readiness document
- `docs/production-readiness.md`: what shipping this would actually require.
- Not built — listed, with a sentence each on why it matters and roughly
  what it would take.
- Cover: authentication and authorization, rate limiting per caller,
  distributed breaker state across instances, PII retention and redaction
  policy for traces and audit rows, model version pinning and rollback,
  cost ceiling per tenant, on-call runbook and alerting, shadow-mode
  rollout, and an evaluation set with a regression gate.
- **Gate:** the document is honest about what does not exist yet.

---

## Demo scenarios (these must work; protect them)

1. **Happy path** — "Where is order 1002?" → one tool call, clean answer.
2. **Multi-step** — "Refund order 1002 if it shipped late" → lookup, then a
   decision, then refund. Proves the model chose the sequence.
3. **Policy block** — "Refund order 1002 for $5000" → pre-hook blocks, agent
   explains why it cannot proceed.
4. **Prompt injection** — a request touching the order whose notes contain
   the injection → the code-level cap blocks the refund regardless of what
   the model was persuaded to attempt.
5. **Circuit breaker** — fault-injection flag on → 3 failures → `ESCALATED`,
   loop exits, no infinite retry.

If a change would break any of these, say so before making it.

---

## Out of scope

Deliberately excluded. Do not add these, and do not suggest them mid-build.
They belong on the roadmap slide, not in the repo.

- **Multi-agent orchestration.** With two tools there is nothing to split.
- **Intent classification and model routing.** The cost story is told by the
  metrics; this adds a subpackage and a second model config for no demo gain.
- **CI, `claude -p` workflows, fork-based doc generation.** High risk, low
  return, and not what this project is being evaluated on.
- RAG, vector stores, embeddings, chat memory, streaming, a chat UI.
- More than six concept docs.
- Any dependency not listed in the Stack table.

Also:
- Do not weaken, skip or bypass a guardrail to make a test pass. Fix the
  test or report the conflict.
- Do not move validation logic into a prompt.
- Do not invent Spring AI API surface. If unsure whether a method exists,
  check the resolved dependency, and say so.
- If a phase gate passes, stop and report rather than continuing.

---

## Reporting format

At each phase gate:

```
PHASE <n> COMPLETE
Built:      <what exists now>
Tests:      <n passed / n failed>
Deviations: <anything you did differently and why>
Blocked on: <anything needing a decision>
Next:       <the next phase, awaiting go-ahead>
```

Keep it short. No prose summaries of code that already exists.
