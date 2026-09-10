package com.example.orderagent.testsupport;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * One recorded model turn: either plain text or a set of tool calls, plus
 * enough metadata to reconstruct a real {@link ChatResponse}.
 *
 * <p>This is deliberately a simplified stand-in for the full Anthropic
 * response shape — just what {@code AgentLoop} actually reads (tool calls,
 * token usage, model name). A cassette file is a JSON array of these.
 */
public record CassetteTurn(String content, List<ToolCall> toolCalls, int promptTokens, int completionTokens, String model) {

    public record ToolCall(String id, String name, String argumentsJson) {
    }

    /** Rebuilds the real Spring AI {@link ChatResponse} this turn represents. */
    public ChatResponse toChatResponse() {
        AssistantMessage.Builder builder = AssistantMessage.builder();
        if (content != null) {
            builder.content(content);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            builder.toolCalls(toolCalls.stream()
                    .map(tc -> new AssistantMessage.ToolCall(tc.id(), "function", tc.name(), tc.argumentsJson()))
                    .toList());
        }
        AssistantMessage assistantMessage = builder.build();

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(promptTokens, completionTokens, promptTokens + completionTokens))
                .build();

        return ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .metadata(metadata)
                .build();
    }

    /** Captures a real {@link ChatResponse} as a {@code CassetteTurn}, for recording. */
    public static CassetteTurn from(ChatResponse response) {
        AssistantMessage output = response.getResult().getOutput();
        List<ToolCall> calls = output.hasToolCalls()
                ? output.getToolCalls().stream()
                        .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
                        .toList()
                : List.of();
        var usage = response.getMetadata().getUsage();
        return new CassetteTurn(
                output.getText(),
                calls,
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                response.getMetadata().getModel());
    }
}
