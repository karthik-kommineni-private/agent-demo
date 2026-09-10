package com.example.orderagent.service.agent;

import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import com.example.orderagent.config.AgentProperties;
import com.example.orderagent.dto.response.AgentResponse;
import com.example.orderagent.dto.tool.SubmitAnswerInput;
import com.example.orderagent.enums.TerminationReason;
import com.example.orderagent.service.tool.ToolRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the agent's decision loop.
 *
 * <p>The model is asked what to do. If it asks for a tool — this is called
 * "tool use": the model's response says "run this tool with these
 * arguments and tell me what happened" instead of giving a final answer —
 * we run that tool and ask again with the result. We keep going until the
 * model calls {@code submit_answer} (Phase 2's schema-forced final step),
 * or until we hit a limit.
 *
 * <p><b>Why a loop and not a fixed sequence:</b> we don't know in advance
 * how many steps a request needs. "Where is order 1002?" needs one lookup.
 * "Refund it if it shipped late" needs a lookup, a judgement, then a
 * refund. The model decides the sequence; this class only keeps it bounded
 * and traced.
 *
 * <p><b>How it stops:</b> the model calls {@code submit_answer} (normal —
 * {@code TerminationReason.COMPLETED}), the iteration cap is hit
 * ({@code ITERATION_CAP}), the token budget is spent ({@code BUDGET_CAP}),
 * a model call fails or times out ({@code MODEL_UNAVAILABLE}), or the
 * model stops calling tools without ever calling {@code submit_answer} —
 * a genuine contract violation, reported as {@code AgentStatus.ERROR}.
 * Every path returns an {@link AgentResponse}; nothing throws out of here.
 *
 * <p>Spring AI 2.0 removed the automatic multi-turn tool-execution loop
 * that used to live inside every {@code ChatModel} — a model call now
 * always returns after one turn, tool calls and all, and driving the next
 * turn is the caller's job. This class is that caller, using
 * {@link ToolCallingManager} to run whichever tools the model asked for
 * rather than reaching into {@link ToolRegistry} directly, so a future
 * governance interceptor wrapped around tool execution (Phase 4) applies
 * here the same way it would to any other caller.
 *
 * @see com.example.orderagent.service.tool.ToolRegistry for where a tool
 *      call actually runs, and where argument validation happens
 */
@Component
public class AgentLoop {

    // Must match SubmitAnswerTool.name(). Not shared as a constant across
    // packages on purpose: the loop only needs to know "some tool ends the
    // conversation," and finds out which one via ToolExecutionResult below,
    // not by importing the tool class. This name is just for locating that
    // tool's own response payload afterward.
    private static final String SUBMIT_ANSWER_TOOL = "submit_answer";

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final ToolRegistry toolRegistry;
    private final SystemPromptLoader systemPromptLoader;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentLoop(
            ChatModel chatModel,
            ToolCallingManager toolCallingManager,
            ToolRegistry toolRegistry,
            SystemPromptLoader systemPromptLoader,
            AgentProperties properties,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolRegistry = toolRegistry;
        this.systemPromptLoader = systemPromptLoader;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves one customer request end to end.
     *
     * @param userRequest the customer's request, in plain English
     * @return the outcome — success, escalation, or error — never an exception
     */
    public AgentResponse run(String userRequest) {
        String traceId = UUID.randomUUID().toString();
        AgentTraceBuilder traceBuilder = new AgentTraceBuilder(traceId, systemPromptLoader.version());

        // Mutating the model's own default options (rather than building a
        // fresh ToolCallingChatOptions from scratch) is what keeps the model
        // name, temperature, etc. configured under spring.ai.anthropic.* —
        // a bare builder() here would silently fall back to the provider's
        // hardcoded default model instead of the one set in application.yml.
        //
        // toolChoice(any) forces every turn to call some tool rather than
        // reply in plain text. Without it, a model can (and in testing,
        // did) just answer in prose instead of calling submit_answer, which
        // would leave AgentLoop with no forced-shape final answer to return
        // — see docs/concepts/04-schema-forced-output.md.
        AnthropicChatOptions defaultOptions = (AnthropicChatOptions) chatModel.getDefaultOptions();
        AnthropicChatOptions.Builder builder = (AnthropicChatOptions.Builder) defaultOptions.mutate();
        ToolCallingChatOptions options = builder.toolCallbacks(toolRegistry.toolCallbacks())
                .toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()))
                .build();

        Prompt prompt = new Prompt(
                List.of(new SystemMessage(systemPromptLoader.content()), new UserMessage(userRequest)), options);

        int totalTokens = 0;
        String modelUsed = null;

        for (int iteration = 1; iteration <= properties.maxIterations(); iteration++) {
            ChatResponse response;
            try {
                response = callModelWithTimeout(prompt);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return AgentResponse.escalated(
                        traceBuilder.build(), iteration - 1, totalTokens, TerminationReason.MODEL_UNAVAILABLE, modelUsed);
            }

            ChatResponseMetadata metadata = response.getMetadata();
            if (metadata != null) {
                modelUsed = metadata.getModel();
                Usage usage = metadata.getUsage();
                if (usage != null && usage.getTotalTokens() != null) {
                    totalTokens += usage.getTotalTokens();
                }
            }

            if (totalTokens > properties.tokenBudget()) {
                return AgentResponse.escalated(
                        traceBuilder.build(), iteration, totalTokens, TerminationReason.BUDGET_CAP, modelUsed);
            }

            if (!response.hasToolCalls()) {
                // Nothing forces a final answer to exist unless the model
                // calls submit_answer. Stopping without doing so is a
                // contract violation, not a normal way to finish.
                return AgentResponse.error(
                        traceBuilder.build(),
                        iteration,
                        totalTokens,
                        "The model stopped without calling submit_answer.",
                        modelUsed);
            }

            AssistantMessage assistantMessage = response.getResult().getOutput();
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                traceBuilder.recordToolCall(iteration, toolCall.name(), toolCall.arguments());
            }

            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);

            if (toolExecutionResult.returnDirect()) {
                String finalAnswer = extractSubmitAnswerMessage(toolExecutionResult.conversationHistory());
                return AgentResponse.success(finalAnswer, traceBuilder.build(), iteration, totalTokens, modelUsed);
            }

            prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
        }

        return AgentResponse.escalated(
                traceBuilder.build(), properties.maxIterations(), totalTokens, TerminationReason.ITERATION_CAP, modelUsed);
    }

    /**
     * Calls the model with a hard wall-clock timeout, so a hung HTTP call
     * to the model provider can't block a request forever. Runs on the
     * common pool since a chat call is I/O-bound, not CPU-bound.
     */
    private ChatResponse callModelWithTimeout(Prompt prompt)
            throws TimeoutException, ExecutionException, InterruptedException {
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() -> chatModel.call(prompt));
        try {
            return future.get(properties.modelCallTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * Pulls the customer-facing message out of {@code submit_answer}'s tool
     * response, which is the last message {@code ToolCallingManager}
     * appended to the conversation.
     */
    private String extractSubmitAnswerMessage(List<Message> conversationHistory) {
        Message lastMessage = conversationHistory.get(conversationHistory.size() - 1);
        if (lastMessage instanceof ToolResponseMessage toolResponseMessage) {
            for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                if (SUBMIT_ANSWER_TOOL.equals(toolResponse.name())) {
                    SubmitAnswerInput input =
                            objectMapper.readValue(toolResponse.responseData(), SubmitAnswerInput.class);
                    return input.message();
                }
            }
        }
        throw new IllegalStateException("submit_answer returned but no matching tool response was found");
    }
}
