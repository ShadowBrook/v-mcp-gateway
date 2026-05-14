package com.mcpgateway.config;

import io.vertx.core.json.JsonObject;

import java.util.Map;

public record ToolConfig(
    String name,
    String description,
    String method,
    String url,
    Map<String, String> headers,
    String bodyTemplate,
    JsonObject inputSchema
) {}
