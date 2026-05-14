package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record CallToolParams(String name, JsonObject arguments) {}
