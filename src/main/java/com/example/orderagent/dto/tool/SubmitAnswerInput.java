package com.example.orderagent.dto.tool;

/**
 * The forced shape of the agent's final answer to the customer.
 *
 * <p>Forcing the model to call {@code submit_answer} rather than just
 * stopping guarantees a {@code message} field exists in the response. It
 * says nothing about whether that message is correct — see
 * docs/concepts/04-schema-forced-output.md.
 */
public record SubmitAnswerInput(String message) {
}
