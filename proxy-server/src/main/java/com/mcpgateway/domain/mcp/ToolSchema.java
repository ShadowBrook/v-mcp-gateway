package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record ToolSchema(
    String name,
    String description,
    JsonObject inputSchema,
    JsonObject annotations,
    JsonObject meta
) {
    public static ToolSchema from(JsonObject json) {
        return new ToolSchema(
            json.getString("name", ""),
            json.getString("description", ""),
            json.getJsonObject("inputSchema"),
            json.getJsonObject("annotations"),
            json.getJsonObject("meta"));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", inputSchema != null ? inputSchema : new JsonObject().put("type", "object"));
        if (annotations != null) json.put("annotations", annotations);
        if (meta != null) json.put("meta", meta);
        return json;
    }
}
