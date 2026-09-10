package com.example.orderagent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orderagent.dto.tool.SubmitAnswerInput;
import com.example.orderagent.exception.ToolExecutionException;
import org.junit.jupiter.api.Test;

class SubmitAnswerToolTest {

    private final SubmitAnswerTool tool = new SubmitAnswerTool();

    @Test
    void acceptsANonBlankMessage() {
        SubmitAnswerInput result = tool.execute(new SubmitAnswerInput("Order 1002 shipped on 2026-08-25."));
        assertThat(result.message()).isEqualTo("Order 1002 shipped on 2026-08-25.");
    }

    @Test
    void rejectsABlankMessage() {
        assertThatThrownBy(() -> tool.execute(new SubmitAnswerInput("   ")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsANullMessage() {
        assertThatThrownBy(() -> tool.execute(new SubmitAnswerInput(null)))
                .isInstanceOf(ToolExecutionException.class);
    }
}
