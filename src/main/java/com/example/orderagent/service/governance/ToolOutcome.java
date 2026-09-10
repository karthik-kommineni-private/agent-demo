package com.example.orderagent.service.governance;

/**
 * What happened to one tool call, passed to every interceptor's post-hook.
 */
public sealed interface ToolOutcome {

    /** The tool ran and returned a result. */
    record Success(Object output) implements ToolOutcome {
    }

    /** The tool ran and threw. This is the only case that counts toward
     *  {@code BreakerRegistry}'s failure count — see its Javadoc for why. */
    record Failure(RuntimeException exception) implements ToolOutcome {
    }

    /** A governance pre-hook refused the call before it ever reached the
     *  tool. Not a health signal, so it does not affect the breaker. */
    record Blocked(String reason) implements ToolOutcome {
    }
}
