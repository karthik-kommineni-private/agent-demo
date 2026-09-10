# Determinism

*Prerequisites: [Agentic loop](01-agentic-loop.md), [Schema-forced output](04-schema-forced-output.md)*

## The problem

A test that calls a real language model can pass today and fail tomorrow
with no code change — the provider updates the model, or the same prompt
just samples a different response. A test suite built on live calls is
also slow, costs real money per run, and can't run at all without a
network connection and an API key. None of that is acceptable for
`mvn clean verify`, which non-negotiable rule 10 requires to pass with no
key present.

## The mechanism

Split what's actually non-deterministic (the model's response to a given
prompt) from everything built around it (the loop, the schemas, the
guardrails, the idempotency store) — and record the non-deterministic
part once, so tests can replay it forever without ever calling out.

A "cassette" here is a JSON file holding a fixed sequence of model
responses. A `CassetteChatModel` implements the real Spring AI
`ChatModel` interface but returns those recorded responses in order
instead of making an HTTP call. Everything downstream of it —
`ToolCallingManager`, `ToolRegistry`, the governance chain — is the real
production code, running exactly as it would against a live model. Only
the model call itself is canned.

## Walk the code

1. **`src/test/java/.../testsupport/CassetteTurn.java`.** The recorded
   shape: text or tool calls, token counts, model name — enough to
   rebuild a real `ChatResponse`, and a `from(ChatResponse)` method that
   does the reverse for recording.

2. **`src/test/java/.../testsupport/CassetteChatModel.java`,
   `call(Prompt)`.** Pops the next recorded turn and returns it. No
   network client, no HTTP call, nothing that can flake or cost money.

3. **`src/test/resources/cassettes/happy-path-lookup.json`.** Not hand-
   written — this file was produced by actually running
   `CassetteRecordingTool` against the real Anthropic API once. Open it
   and compare it to the live response shown in the project README.

4. **`src/test/java/.../testsupport/RecordingChatModel.java` and
   `CassetteRecordingTool.java`.** The other half: wraps a real
   `ChatModel`, captures every call, and writes the cassette. Gated
   behind `RECORD_CASSETTES=true` so it never runs during
   `mvn clean verify` — it makes a real, billed call by design.

5. **`AgentLoopGoldenTest.java`.** Wires a `CassetteChatModel` into a
   real `AgentLoop`, real `ToolRegistry`, real governance interceptors,
   and a real `DefaultToolCallingManager`. Only `OrderService` and
   `AuditService` are mocked. This is the test that's actually
   deterministic end to end.

## Code

```java
// CassetteChatModel.java
@Override
public ChatResponse call(Prompt prompt) {
    promptsSeen.add(prompt);
    CassetteTurn turn = turns.pollFirst();
    if (turn == null) {
        throw new IllegalStateException("Cassette exhausted after " + promptsSeen.size() + " calls");
    }
    return turn.toChatResponse();
}
```

## Try changing it

Open `cassettes/happy-path-lookup.json` and edit the second turn's
`argumentsJson` to a different `message` string. Run
`AgentLoopGoldenTest` again — it now asserts against your edited text and
passes, because the test genuinely doesn't care where the `ChatResponse`
came from. This is also how you'd catch a real regression: if
`extractSubmitAnswerMessage` broke, this test would fail without needing
a live model call to prove it.

## What it does NOT solve

**It doesn't make production deterministic.** A real customer's request
still goes to a real model, which can still answer differently on retry.
Cassette replay makes the *test suite* deterministic; it says nothing
about run-to-run consistency in production, which this project doesn't
attempt to guarantee.

**It's only as good as what got recorded.** `happy-path-lookup.json`
captures one specific exchange. If the model's real behavior for that
same prompt later shifts — a new model version, a changed system prompt —
the cassette doesn't know that changed until someone re-records it with
`RECORD_CASSETTES=true`. A passing golden test proves the code handles
*this* recorded response correctly, not that it's still the response a
live call would produce.

**What actually is deterministic:** the iteration cap, the token budget,
the refund cap arithmetic, the strict JSON schema validation, and the
refund idempotency store all behave identically on every run, given the
same inputs — that's ordinary code, not a language model. The model's
choice of *which* tool to call and *what* to say is the one genuinely
non-deterministic piece this project doesn't try to pin down.

## Related

- [Agentic loop](01-agentic-loop.md) — the deterministic scaffolding
  wrapped around the non-deterministic model call
- [Schema-forced output](04-schema-forced-output.md) — a guarantee about
  shape that holds regardless of what the model actually says

## See it run

```bash
unset ANTHROPIC_API_KEY
mvn clean verify
```

Expected: the full suite passes, `AgentLoopGoldenTest` included, with no
network call and no key. To regenerate the cassette itself (real, billed
call):

```bash
RECORD_CASSETTES=true ANTHROPIC_API_KEY=sk-... ./mvnw test -Dtest=CassetteRecordingTool
```
