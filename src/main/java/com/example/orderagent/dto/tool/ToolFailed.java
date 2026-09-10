package com.example.orderagent.dto.tool;

/**
 * What the model receives instead of a tool's real output when the tool
 * itself threw during execution — as opposed to {@link ToolBlocked}, which
 * means a governance check refused the call before it ever ran.
 */
public record ToolFailed(boolean failed, String reason) {

    public ToolFailed(String reason) {
        this(true, reason);
    }
}
