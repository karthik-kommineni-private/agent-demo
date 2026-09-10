# Schema-forced output

*Prerequisites: [Agentic loop](01-agentic-loop.md)*

## The problem

The model finishes helping a customer and just... writes a sentence.
"Sure, I've refunded that for you!" No tool call, no structure — the
loop has no `submit_answer` message to return, no reliable way to tell
"the model is done and here's the answer" apart from "the model stopped
talking mid-task." This isn't hypothetical: during development of this
project, the model did exactly this against a live API call, and the
request had to fail closed instead of returning an answer.

## The mechanism

Make "give the final answer" a tool call like any other, with its own
JSON Schema requiring a `message` field. Then force the model to always
call *some* tool, every turn, rather than ever replying in free text.

Two things combine to make this work. First, `submit_answer`'s schema
guarantees that if it's called, a non-empty `message` exists — the model
can't call it with nothing to say. Second, forcing tool use on every turn
(Anthropic's `tool_choice: any`) means the model is never actually
choosing between "call a tool" and "just talk" — talking isn't an option,
so eventually it has to call `submit_answer` to end the conversation.

## Walk the code

1. **`service/tool/SubmitAnswerTool.java`.** The whole tool: one field,
   `message`, required and non-blank. Its `inputSchema()` is the actual
   contract sent to the model — read this before anything else.

2. **`service/tool/OrderAgentTool.java`, `endsTheLoop()`.** A default
   method returning `false`, overridden `true` only here. This is how the
   loop finds out "this specific call means we're done" without checking
   a hardcoded tool name at the point of decision.

3. **`service/tool/ToolRegistry.java`, `asToolCallback`.** Where
   `endsTheLoop()` becomes `ToolMetadata.returnDirect(true)` — a real
   Spring AI concept, not something this project invented. Once
   `ToolCallingManager` executes a `returnDirect` tool, it reports that
   back on `ToolExecutionResult`, which is what `AgentLoop` actually
   checks.

4. **`service/agent/AgentLoop.java`, the `toolChoice(ToolChoice.ofAny(...))`
   call when building chat options.** This is the forcing part — without
   it, the model is free to end its turn with plain text instead of a
   tool call, and the mechanism above never gets a chance to run.

5. **`service/agent/AgentLoop.java`, `extractSubmitAnswerMessage`.**
   Pulls `message` back out of the tool's own response, rather than
   trusting the model's original arguments — the same "don't trust
   unvalidated input" instinct as everywhere else in this project.

## Code

```java
// SubmitAnswerTool.java
private static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "message": { "type": "string", "minLength": 1, "description": "..." }
          },
          "required": ["message"],
          "additionalProperties": false
        }
        """;

@Override
public boolean endsTheLoop() {
    return true;
}
```

```java
// AgentLoop.java — forces a tool call every turn, never plain text
ToolCallingChatOptions options = builder.toolCallbacks(toolRegistry.toolCallbacks(traceId))
        .toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()))
        .build();
```

## Try changing it

Remove the `.toolChoice(...)` line and run
`AgentLoopTest#errorsWhenTheModelStopsWithoutCallingSubmitAnswer` — it
already exists specifically for this failure mode and still passes,
because that test mocks the model's response directly. To see the real
effect, remove the line and hit a live model with a simple status
question. Some responses will still call `submit_answer` out of habit
from the system prompt; others will just answer in text, and the request
comes back `"status":"ERROR"` — this is the actual failure this project
hit during Phase 3 development, which is why the line exists.

## What it does NOT solve

The schema guarantees a `message` field exists and is non-blank. It says
nothing about whether that message is true, complete, or a good answer to
the customer's actual question. A model that hallucinates an order's ship
date will pass this check just as cleanly as one that got it right —
shape and correctness are different guarantees, and this mechanism only
buys the first one.

## Related

- [Agentic loop](01-agentic-loop.md) — where `returnDirect` actually ends
  the `for` loop
- [Determinism](06-determinism.md) — schema-checked shape is one of the
  few genuinely deterministic guarantees this project makes

## See it run

```bash
mvn test -Dtest=SubmitAnswerToolTest
```

Expected: 3 tests pass — a non-blank message accepted, a blank message
rejected, a null message rejected. For the forced-tool-choice half of the
mechanism, see `AgentLoopGoldenTest`, whose replayed cassette ends in a
`submit_answer` call because the live recording that produced it was made
with `tool_choice: any` in effect.
