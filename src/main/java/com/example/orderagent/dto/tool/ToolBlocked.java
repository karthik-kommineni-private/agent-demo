package com.example.orderagent.dto.tool;

/**
 * What the model receives instead of a tool's real output when a
 * governance pre-hook refuses the call. The reason is meant for the
 * <i>model</i> to read and relay, not just for logs — see
 * {@code service.governance.ToolInterceptor}.
 */
public record ToolBlocked(boolean blocked, String reason) {

    public ToolBlocked(String reason) {
        this(true, reason);
    }
}
