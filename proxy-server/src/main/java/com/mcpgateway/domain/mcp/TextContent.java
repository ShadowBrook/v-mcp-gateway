package com.mcpgateway.domain.mcp;

public record TextContent(String type, String text) implements Content {
    public static final String TYPE = "text";

    public static TextContent of(String text) {
        return new TextContent(TYPE, text);
    }
}
