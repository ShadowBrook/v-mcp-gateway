package com.mcpgateway.domain.mcp;

import java.util.List;

public record CallToolResult(List<Content> content, boolean isError) {

    public static CallToolResult success(List<Content> content) {
        return new CallToolResult(content, false);
    }

    public static CallToolResult error(List<Content> content) {
        return new CallToolResult(content, true);
    }
}
