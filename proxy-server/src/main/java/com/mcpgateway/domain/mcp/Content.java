package com.mcpgateway.domain.mcp;

public sealed interface Content
    permits TextContent, ImageContent, AudioContent {

    String type();
}
