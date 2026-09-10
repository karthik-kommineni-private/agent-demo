package com.example.orderagent.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wraps a real {@link ChatModel}, capturing every call as a
 * {@link CassetteTurn} and writing them to a cassette file — how the
 * cassettes under {@code src/test/resources/cassettes/} were made.
 *
 * <p>Not used by {@code mvn clean verify}, and not wired into the Spring
 * context anywhere. This exists purely as a manual tool: point it at a
 * real {@code ChatModel} (with {@code ANTHROPIC_API_KEY} set), drive
 * {@code AgentLoop} through a scenario once, then call {@link #save} to
 * regenerate the cassette a golden test replays forever after, at zero
 * cost and with no network dependency.
 */
public class RecordingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final List<CassetteTurn> recorded = new ArrayList<>();

    public RecordingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse response = delegate.call(prompt);
        recorded.add(CassetteTurn.from(response));
        return response;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    /** Writes everything recorded so far to {@code path} as a JSON array. */
    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(recorded));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write cassette to " + path, e);
        }
    }
}
