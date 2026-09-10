package com.example.orderagent.service.tool;

import com.example.orderagent.dto.tool.SubmitAnswerInput;
import com.example.orderagent.exception.ToolExecutionException;
import org.springframework.stereotype.Component;

/**
 * The forced final step of every request: the model must call this tool to
 * hand back its answer, rather than simply stopping.
 *
 * <p>This buys shape, not correctness. Whatever the model would otherwise
 * have said in free text, it now has to put in a {@code message} field, so
 * {@code AgentLoop} (Phase 3) can recognize the end of a request by tool
 * name — {@code stop_reason} alone can't tell "done" apart from "gave up
 * mid-sentence." See docs/concepts/04-schema-forced-output.md.
 *
 * <p><b>How it fails:</b> a blank message is rejected. There is nothing
 * else to check — this tool doesn't decide whether the message is a good
 * answer, only that one exists.
 */
@Component
public class SubmitAnswerTool implements OrderAgentTool<SubmitAnswerInput, SubmitAnswerInput> {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "message": {
                  "type": "string",
                  "minLength": 1,
                  "description": "The final answer to give the customer."
                }
              },
              "required": ["message"],
              "additionalProperties": false
            }
            """;

    @Override
    public String name() {
        return "submit_answer";
    }

    @Override
    public String description() {
        return "Submits the final answer to the customer. Call this exactly once, when you are done.";
    }

    @Override
    public String inputSchema() {
        return INPUT_SCHEMA;
    }

    @Override
    public Class<SubmitAnswerInput> inputType() {
        return SubmitAnswerInput.class;
    }

    @Override
    public boolean endsTheLoop() {
        return true;
    }

    @Override
    public SubmitAnswerInput execute(SubmitAnswerInput input) {
        if (input.message() == null || input.message().isBlank()) {
            throw new ToolExecutionException(name(), "message must not be blank");
        }
        return input;
    }
}
