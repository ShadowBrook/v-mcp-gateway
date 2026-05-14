package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record PromptArgument(String name, String description, boolean required) {
    public static PromptArgument from(JsonObject json) {
        return new PromptArgument(
            json.getString("name", ""),
            json.getString("description", ""),
            json.getBoolean("required", false));
    }
}
