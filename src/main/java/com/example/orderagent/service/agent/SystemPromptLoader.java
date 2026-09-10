package com.example.orderagent.service.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the agent's system prompt from {@code prompts/order-agent-system-prompt.md}.
 *
 * <p>The prompt is never a Java string literal. Keeping it in a versioned
 * markdown file means it can be edited, reviewed and diffed like any other
 * part of the project's behavior, and the version header lets every trace
 * record exactly which wording of the prompt produced a given response —
 * useful the day someone asks "did this answer change because we edited
 * the prompt?"
 */
@Component
public class SystemPromptLoader {

    private static final Pattern VERSION_HEADER = Pattern.compile("<!--\\s*prompt-version:\\s*(\\S+)\\s*-->");

    private final String content;
    private final String version;

    public SystemPromptLoader(@Value("classpath:prompts/order-agent-system-prompt.md") Resource promptResource) {
        String raw = readResource(promptResource);
        Matcher matcher = VERSION_HEADER.matcher(raw);
        this.version = matcher.find() ? matcher.group(1) : "unknown";
        this.content = raw;
    }

    private static String readResource(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt from " + resource, e);
        }
    }

    /** The full prompt text, including its version header. */
    public String content() {
        return content;
    }

    /** The version declared in the prompt's header comment. */
    public String version() {
        return version;
    }
}
