package com.example.orderagent.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderagent.config.AgentProperties;
import com.example.orderagent.dto.response.AgentResponse;
import com.example.orderagent.service.agent.AgentLoop;
import com.example.orderagent.service.agent.SystemPromptLoader;
import com.example.orderagent.service.agent.TokenMeter;
import com.example.orderagent.service.governance.BreakerRegistry;
import com.example.orderagent.service.tool.ToolRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Regenerates the golden-test cassettes against the real Anthropic API.
 *
 * <p>Not a test in the usual sense — it makes a real, billed model call.
 * Disabled unless {@code RECORD_CASSETTES=true} is set, so it never runs
 * as part of {@code mvn clean verify}. To regenerate the happy-path
 * cassette:
 *
 * <pre>{@code
 * RECORD_CASSETTES=true ANTHROPIC_API_KEY=sk-... \
 *   ./mvnw test -Dtest=CassetteRecordingTool
 * }</pre>
 *
 * <p>Builds its own {@link AgentLoop} instance — wired with the same real
 * beans the app uses, except the {@link ChatModel} is wrapped in
 * {@link RecordingChatModel} — rather than using the app's own
 * Spring-managed {@code AgentLoop} bean, since that bean has no seam for
 * substituting a recording wrapper without changing production code for a
 * recording-only concern.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RECORD_CASSETTES", matches = "true")
class CassetteRecordingTool {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolCallingManager toolCallingManager;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private SystemPromptLoader systemPromptLoader;

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BreakerRegistry breakerRegistry;

    @Autowired
    private TokenMeter tokenMeter;

    @Test
    void recordHappyPathCassette() {
        RecordingChatModel recorder = new RecordingChatModel(chatModel);
        AgentLoop recordingLoop = new AgentLoop(
                recorder,
                toolCallingManager,
                toolRegistry,
                systemPromptLoader,
                agentProperties,
                objectMapper,
                breakerRegistry,
                tokenMeter);

        AgentResponse response = recordingLoop.run("Where is order 1001?");
        assertThat(response.message()).isNotBlank();

        Path cassette = Path.of("src/test/resources/cassettes/happy-path-lookup.json");
        recorder.save(cassette);
        System.out.println("Wrote cassette to " + cassette.toAbsolutePath());
    }
}
