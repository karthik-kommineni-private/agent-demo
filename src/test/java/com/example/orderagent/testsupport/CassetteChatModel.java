package com.example.orderagent.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link ChatModel} that replays a recorded cassette instead of calling a
 * real model — this is what makes a golden-file test both deterministic
 * and free. Every other collaborator in a test using this
 * ({@code ToolCallingManager}, {@code ToolRegistry}, the governance chain)
 * is the real thing; only the model call itself is canned.
 *
 * @see CassetteTurn for the recorded shape
 */
public class CassetteChatModel implements ChatModel {

    private final Deque<CassetteTurn> turns;
    private final List<Prompt> promptsSeen = new ArrayList<>();

    public CassetteChatModel(List<CassetteTurn> turns) {
        this.turns = new ArrayDeque<>(turns);
    }

    /** Loads a cassette written as a JSON array of {@link CassetteTurn}. */
    public static CassetteChatModel loadFromClasspath(String resourcePath) {
        try (InputStream in = CassetteChatModel.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("No such cassette: " + resourcePath);
            }
            JsonMapper mapper = JsonMapper.builder().build();
            List<CassetteTurn> turns = List.of(mapper.readValue(in.readAllBytes(), CassetteTurn[].class));
            return new CassetteChatModel(turns);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load cassette " + resourcePath, e);
        }
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        promptsSeen.add(prompt);
        CassetteTurn turn = turns.pollFirst();
        if (turn == null) {
            throw new IllegalStateException("Cassette exhausted after " + promptsSeen.size() + " calls");
        }
        return turn.toChatResponse();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        // AgentLoop casts this to AnthropicChatOptions to preserve model
        // config across mutate() — the cassette needs to hand back the same
        // concrete type a real AnthropicChatModel would.
        return AnthropicChatOptions.builder().model("claude-haiku-4-5-20251001").build();
    }

    /** How many calls this cassette actually received, for assertions. */
    public int callCount() {
        return promptsSeen.size();
    }
}
