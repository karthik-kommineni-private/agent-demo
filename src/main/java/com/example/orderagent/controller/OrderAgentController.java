package com.example.orderagent.controller;

import com.example.orderagent.dto.request.AgentRequest;
import com.example.orderagent.dto.response.AgentResponse;
import com.example.orderagent.service.agent.AgentLoop;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint this project has. Takes a customer request in plain
 * English and returns the agent's outcome.
 *
 * <p>No business logic or AI calls happen here — this class only adapts
 * HTTP to {@link AgentLoop}, which is why {@link GlobalExceptionHandler}
 * (not this class) is what guarantees every response, error included, has
 * the same {@link AgentResponse} shape.
 */
@RestController
class OrderAgentController {

    private final AgentLoop agentLoop;

    OrderAgentController(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    @PostMapping("/orders/agent")
    AgentResponse handle(@Valid @RequestBody AgentRequest request) {
        return agentLoop.run(request.request());
    }
}
