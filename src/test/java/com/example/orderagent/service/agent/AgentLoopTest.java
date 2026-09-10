package com.example.orderagent.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.orderagent.config.AgentProperties;
import com.example.orderagent.dto.response.AgentResponse;
import com.example.orderagent.enums.AgentStatus;
import com.example.orderagent.enums.TerminationReason;
import com.example.orderagent.service.OrderService;
import com.example.orderagent.service.tool.IssueRefundTool;
import com.example.orderagent.service.tool.LookupOrderTool;
import com.example.orderagent.service.tool.OrderAgentTool;
import com.example.orderagent.service.tool.SubmitAnswerTool;
import com.example.orderagent.service.tool.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives {@link AgentLoop} against a mocked {@link ChatModel} and
 * {@link ToolCallingManager} so the loop's own control flow — iteration
 * counting, stopping on {@code submit_answer}, failing closed when the
 * model doesn't comply — is tested without a live model call. This is the
 * Phase 3 gate: "Where is order 1002?" resolving end to end with one tool
 * call, expressed as a test instead of a manual curl.
 */
@ExtendWith(MockitoExtension.class)
class AgentLoopTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ToolCallingManager toolCallingManager;

    @Mock
    private OrderService orderService;

    private AgentLoop newAgentLoop(AgentProperties properties) {
        // AgentLoop mutates the model's own default options (to preserve the
        // configured model name) rather than building fresh ones, so the
        // mock must return something real to mutate.
        when(chatModel.getDefaultOptions()).thenReturn(AnthropicChatOptions.builder().model("claude-sonnet-5").build());

        List<OrderAgentTool<?, ?>> tools =
                List.of(new LookupOrderTool(orderService), new IssueRefundTool(orderService), new SubmitAnswerTool());
        ToolRegistry toolRegistry = new ToolRegistry(tools, JsonMapper.builder().build());
        SystemPromptLoader promptLoader =
                new SystemPromptLoader(new ClassPathResource("prompts/order-agent-system-prompt.md"));
        return new AgentLoop(
                chatModel, toolCallingManager, toolRegistry, promptLoader, properties, JsonMapper.builder().build());
    }

    private ChatResponse toolCallResponse(String toolCallId, String toolName, String argumentsJson) {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(toolCallId, "function", toolName, argumentsJson);
        AssistantMessage assistantMessage =
                AssistantMessage.builder().toolCalls(List.of(toolCall)).build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("claude-sonnet-5")
                .usage(new DefaultUsage(100, 20, 120))
                .build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .metadata(metadata)
                .build();
    }

    private ChatResponse textOnlyResponse(String text) {
        AssistantMessage assistantMessage = AssistantMessage.builder().content(text).build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("claude-sonnet-5")
                .usage(new DefaultUsage(50, 10, 60))
                .build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .metadata(metadata)
                .build();
    }

    private ToolExecutionResult toolExecutionResult(
            List<Message> history, boolean returnDirect) {
        return ToolExecutionResult.builder().conversationHistory(history).returnDirect(returnDirect).build();
    }

    @Test
    void resolvesTheHappyPathWithOneToolCall() {
        // ToolCallingManager is mocked here, so no tool's execute() logic
        // actually runs — this test is about AgentLoop's own control flow
        // (stopping on submit_answer, tracing, token totals), not tool
        // correctness, which LookupOrderToolTest and ToolRegistryTest cover.
        ChatResponse lookupResponse = toolCallResponse("call-1", "lookupOrder", "{\"orderId\":1002}");
        ChatResponse submitResponse =
                toolCallResponse("call-2", "submit_answer", "{\"message\":\"Order 1002 shipped on 2026-08-25.\"}");

        ToolResponseMessage.ToolResponse submitAnswerToolResponse = new ToolResponseMessage.ToolResponse(
                "call-2", "submit_answer", "{\"message\":\"Order 1002 shipped on 2026-08-25.\"}");
        ToolResponseMessage finalToolResponseMessage =
                ToolResponseMessage.builder().responses(List.of(submitAnswerToolResponse)).build();

        ToolResponseMessage.ToolResponse lookupToolResponse =
                new ToolResponseMessage.ToolResponse("call-1", "lookupOrder", "{\"orderId\":1002}");
        ToolResponseMessage lookupToolResponseMessage =
                ToolResponseMessage.builder().responses(List.of(lookupToolResponse)).build();

        when(chatModel.call(any(Prompt.class))).thenReturn(lookupResponse, submitResponse);
        when(toolCallingManager.executeToolCalls(any(Prompt.class), eq(lookupResponse)))
                .thenReturn(toolExecutionResult(List.of(lookupToolResponseMessage), false));
        when(toolCallingManager.executeToolCalls(any(Prompt.class), eq(submitResponse)))
                .thenReturn(toolExecutionResult(List.of(finalToolResponseMessage), true));

        AgentResponse response = newAgentLoop(new AgentProperties(6, 20000, 30)).run("Where is order 1002?");

        assertThat(response.status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(response.message()).isEqualTo("Order 1002 shipped on 2026-08-25.");
        assertThat(response.iterations()).isEqualTo(2);
        assertThat(response.totalTokens()).isEqualTo(240);
        assertThat(response.terminationReason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(response.trace().steps()).hasSize(2);
        assertThat(response.trace().steps().get(0).toolName()).isEqualTo("lookupOrder");
        assertThat(response.trace().steps().get(1).toolName()).isEqualTo("submit_answer");
    }

    @Test
    void escalatesWhenTheIterationCapIsHit() {
        ChatResponse lookupResponse = toolCallResponse("call-1", "lookupOrder", "{\"orderId\":1002}");
        ToolResponseMessage.ToolResponse lookupToolResponse =
                new ToolResponseMessage.ToolResponse("call-1", "lookupOrder", "{\"orderId\":1002}");
        ToolResponseMessage lookupToolResponseMessage =
                ToolResponseMessage.builder().responses(List.of(lookupToolResponse)).build();

        when(chatModel.call(any(Prompt.class))).thenReturn(lookupResponse);
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(toolExecutionResult(List.of(lookupToolResponseMessage), false));

        AgentResponse response = newAgentLoop(new AgentProperties(3, 20000, 30)).run("Refund order 1002 please.");

        assertThat(response.status()).isEqualTo(AgentStatus.ESCALATED);
        assertThat(response.terminationReason()).isEqualTo(TerminationReason.ITERATION_CAP);
        assertThat(response.iterations()).isEqualTo(3);
    }

    @Test
    void escalatesWhenTheTokenBudgetIsExceeded() {
        ChatResponse lookupResponse = toolCallResponse("call-1", "lookupOrder", "{\"orderId\":1002}");
        when(chatModel.call(any(Prompt.class))).thenReturn(lookupResponse);

        AgentResponse response = newAgentLoop(new AgentProperties(6, 100, 30)).run("Where is order 1002?");

        assertThat(response.status()).isEqualTo(AgentStatus.ESCALATED);
        assertThat(response.terminationReason()).isEqualTo(TerminationReason.BUDGET_CAP);
    }

    @Test
    void errorsWhenTheModelStopsWithoutCallingSubmitAnswer() {
        when(chatModel.call(any(Prompt.class))).thenReturn(textOnlyResponse("I think it's fine."));

        AgentResponse response = newAgentLoop(new AgentProperties(6, 20000, 30)).run("Where is order 1002?");

        assertThat(response.status()).isEqualTo(AgentStatus.ERROR);
        assertThat(response.message()).contains("submit_answer");
    }
}
