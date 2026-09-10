# Production readiness

What this project has is a working demonstration of a governance pattern.
What it does not have is any of the things a real deployment handling real
customer refunds would need before it could accept live traffic. This
document lists those things honestly — not built, not started in most
cases — with a sentence on why each matters and roughly what it would
take.

Nothing in this document is implemented. If any of it later becomes true,
update this file to say so; a stale "not built" claim is worse than none.

## Authentication and authorization

**Why it matters.** `POST /orders/agent` currently accepts any request
from anyone who can reach port 8080. There is no notion of which customer
or support agent is making a request, so there is nothing to check a
refund against ("is this person allowed to refund *this* order") beyond
the order-level policy caps this project does have.

**What it would take.** A caller identity (an API key, a session token, an
OAuth flow — whatever the surrounding system already uses), threaded
through to `PolicyInterceptor` so authorization becomes part of the same
pre-hook chain that already checks refund caps. The interceptor pattern
this project uses is built for exactly this kind of addition.

## Rate limiting per caller

**Why it matters.** Nothing stops one caller from sending enough requests
to exhaust the token budget this project *does* enforce per-request, run
up a large model bill, or simply crowd out other traffic. `AgentProperties`
bounds a single request's cost; nothing bounds a caller's aggregate cost
over time.

**What it would take.** A rate limiter keyed by caller identity (which
requires the authentication above first), likely as a filter in front of
`OrderAgentController` — a bucket algorithm (token bucket, sliding
window) is standard here, and several exist as Spring-compatible
libraries already.

## Distributed breaker state across instances

**Why it matters.** `BreakerRegistry` (see
[`03-circuit-breaker.md`](concepts/03-circuit-breaker.md)) is a
`ConcurrentHashMap` local to one JVM. Run three instances behind a load
balancer and each has its own failure count — a tool failing on every
instance simultaneously would need three times the failures to trip any
one instance's breaker, and an instance that gets lucky with routing
might never see enough failures to open at all.

**What it would take.** Shared state — Redis is the usual choice — with
the same consecutive-failure semantics, or a real Resilience4j
`CircuitBreaker` backed by a shared state store instead of this project's
hand-rolled in-memory counter.

## PII retention and redaction policy for traces and audit rows

**Why it matters.** `RedactionInterceptor` regexes email addresses out of
what `AuditInterceptor` persists — that's the entire PII story today.
There's no retention period (rows live in the `audit_log` table forever),
no policy for what other fields count as PII (a customer's name in a
refund `reason` field would sail through untouched), and no deletion
mechanism for a "forget this customer" request.

**What it would take.** A real data classification pass over every field
that reaches `AuditLog` or `AgentTrace`, a retention/deletion policy that
satisfies whatever regulatory regime applies (GDPR, CCPA, or otherwise),
and redaction that's driven by field-level annotations rather than a
single regex that only happens to catch email addresses.

## Model version pinning and rollback

**Why it matters.** `application.yml` names a model
(`claude-haiku-4-5-20251001`) but nothing stops a provider-side model
update from changing behavior under this exact same config. There's no
mechanism to detect a behavior regression after a model update, and no
tested rollback path.

**What it would take.** Pinning to a specific model snapshot where the
provider offers one, an evaluation gate (below) that runs against a
candidate model version before it's promoted, and a documented rollback
— which for this project would be as simple as reverting the config
value, but that path has never actually been exercised.

## Cost ceiling per tenant

**Why it matters.** `TokenMeter` computes cost per request and publishes
it to Micrometer, but nothing aggregates that across a tenant or alerts
when a tenant's spend crosses a threshold. A single misbehaving caller (or
a caller with unusually verbose requests) could run up cost with no
circuit breaker on the money, only on tool failures.

**What it would take.** Cost aggregation per tenant (likely the same
identity work as authentication, again), a configurable ceiling, and a
decision about what happens when it's hit — throttle, degrade to a
cheaper model, or refuse outright.

## On-call runbook and alerting

**Why it matters.** `ESCALATED` responses exist so a human can pick up
what the agent couldn't finish, but nothing in this project pages anyone,
routes an escalation to a queue, or documents what a human should
actually do when they see `BREAKER_OPEN` versus `BUDGET_CAP` versus
`ITERATION_CAP`. Today, an escalation is a JSON response and nothing
more.

**What it would take.** A real destination for escalated requests (a
ticket queue, a Slack channel, a dashboard), alerting on breaker-open
events and elevated error rates via the Micrometer counters this project
already emits, and a runbook explaining what each termination reason
means operationally and what the on-call response should be.

## Shadow-mode rollout

**Why it matters.** This project's roadmap (see the README) names shadow
mode — running the agent alongside human handling, logging what it would
have done, acting on nothing — as the safe way to earn the right to act
on real orders. Nothing here does that; every request that reaches
`AgentLoop` today can execute a real (governed, capped, but real) refund.

**What it would take.** A mode where `IssueRefundTool` logs the refund it
would have made instead of calling `OrderService.applyRefund`, run
against real traffic for long enough to compare the agent's decisions
against what human agents actually did, before ever flipping it to live.

## An evaluation set with a regression gate

**Why it matters.** This project's tests check that the code does what
the code is supposed to do — caps hold, the breaker trips, the loop
terminates. None of them check whether the *agent's decisions* are good
ones on a broader set of realistic requests, and nothing would catch a
prompt change or model swap that quietly made the agent worse at its
actual job.

**What it would take.** A labeled set of realistic requests with expected
outcomes (which tool sequence, what kind of answer), run against every
prompt or model change, with a threshold that fails the build on
regression — the same idea as `AgentLoopGoldenTest`
(see [`06-determinism.md`](concepts/06-determinism.md)), but judging
decision quality across many scenarios instead of replaying one fixed
exchange.
