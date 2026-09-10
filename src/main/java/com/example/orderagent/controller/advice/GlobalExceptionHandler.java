package com.example.orderagent.controller.advice;

import com.example.orderagent.dto.response.AgentResponse;
import java.math.BigDecimal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Guarantees non-negotiable rule 5: every response from this service,
 * including a malformed request or an unexpected failure, is an
 * {@link AgentResponse} — never a raw stack trace or a framework-default
 * error body.
 *
 * <p>This is deliberately the only place that turns "something threw" into
 * a response. {@link com.example.orderagent.service.agent.AgentLoop}
 * already catches everything it knows how to interpret (a failed model
 * call, a tool failure) and returns a specific {@code AgentResponse}
 * itself; what reaches here is either a request that failed validation
 * before the loop ever ran, or a failure {@code AgentLoop} didn't
 * anticipate. Either way, this fails closed rather than leaking detail
 * about the failure to the caller.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    AgentResponse handleValidationFailure(MethodArgumentNotValidException e) {
        return AgentResponse.error(null, 0, 0, BigDecimal.ZERO, "The request was malformed: " + e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    AgentResponse handleUnexpectedFailure(Exception e) {
        return AgentResponse.error(null, 0, 0, BigDecimal.ZERO, "The request could not be processed.", null);
    }
}
