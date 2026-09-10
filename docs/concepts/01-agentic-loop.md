# Agentic loop

*Prerequisites: none — start here*

## The problem

"Where is order 1002?" needs one lookup and an answer. "Refund it if it
shipped late" needs a lookup, a judgement about what "late" means, and
then a refund. A fixed sequence of steps can handle the first request or
the second, but not both without someone writing a branch for every
possible request shape in advance.

## The mechanism

"Tool use" is the model's way of saying "run this for me and tell me what
happened" instead of answering directly — its response names a tool and
gives arguments, rather than text. The loop asks the model what to do,
and if the response is tool use, runs that tool and asks again with the
result. This repeats until the model calls a specific "I'm done" tool, or
a limit is hit.

The model decides how many times to go around. The code's only job is to
run whatever tool was asked for, feed the result back, and stop the loop
if it runs too long — never to decide *how many* steps a request needs.

## Walk the code

1. **`src/main/java/com/example/orderagent/service/agent/AgentLoop.java`,
   the `for` loop in `run()`.** This is the whole mechanism: ask the
   model, check whether it asked for a tool, run it, ask again. Read this
   before anything else in the class.
2. **`ChatResponse.hasToolCalls()`, checked right after the model call.**
   If false, the model tried to just talk instead of finishing through
   `submit_answer` — see `04-schema-forced-output.md` for why that's
   treated as a failure, not a normal answer.
3. **`ToolExecutionResult.returnDirect()`, checked after running the
   tool(s).** This is how the loop tells "the model is done" apart from
   "the model wants to keep working," without checking a tool name
   directly — see `service/tool/OrderAgentTool.endsTheLoop()`.
4. **`AgentProperties.maxIterations()`**, read once at the top of the
   `for` loop's bound. This is the hard stop that makes "the model decides
   how many steps" safe rather than open-ended.

## Code

```java
// AgentLoop.java
for (int iteration = 1; iteration <= properties.maxIterations(); iteration++) {
    ChatResponse response = callModelWithTimeout(prompt);
    // ... token accounting, budget check ...

    if (!response.hasToolCalls()) {
        return AgentResponse.error(/* the model never called submit_answer */);
    }

    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
    if (toolExecutionResult.returnDirect()) {
        return AgentResponse.success(/* submit_answer's message */);
    }
    prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
}
```

## Try changing it

Set `orderagent.max-iterations: 1` in `application.yml` and ask "Refund
order 1002 if it shipped late." The request needs a lookup and then a
refund — two tool calls — so it can't finish in one iteration. The
response comes back `ESCALATED` with `terminationReason: ITERATION_CAP`
instead of a refund, even though the model would have handled it fine
given room to.

## What it does NOT solve

The cap bounds cost and time, but it has no idea whether the model is
making progress toward an answer or spinning. A model stuck re-reading
the same order five times in a row hits the same iteration cap as one
doing genuinely productive multi-step work — the loop can't tell the
difference, because it isn't reasoning about the request itself.

## Related

- [Schema-forced output](04-schema-forced-output.md) — why the loop can
  tell "done" from "still working" without inspecting free text
- [Circuit breaker](03-circuit-breaker.md) — the other way the loop stops
  itself early
- [Determinism](06-determinism.md) — what's fixed about this loop's
  behavior and what depends on the model

## See it run

```bash
mvn test -Dtest=AgentLoopGoldenTest
```

Expected: the test passes, replaying a recorded two-turn exchange
(`lookupOrder` then `submit_answer`) and asserting exactly that tool
sequence and the final message — no network call, no API key needed.
