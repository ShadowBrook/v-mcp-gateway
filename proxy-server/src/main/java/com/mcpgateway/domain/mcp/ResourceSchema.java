package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record ResourceSchema(
    String uri,
    String name,
    String description,
    String mimeType,
    Long size,
    JsonObject annotations
) {
    public static ResourceSchema from(JsonObject json) {
        return new ResourceSchema(
            json.getString("uri", ""),
            json.getString("name", ""),
            json.getString("description", ""),
            json.getString("mimeType"),
            json.getLong("size"),
            json.getJsonObject("annotations"));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("uri", uri)
            .put("name", name);
        if (description != null) json.put("description", description);
        if (mimeType != null) json.put("mimeType", mimeType);
        if (size != null) json.put("size", size);
        if (annotations != null) json.put("annotations", annotations);
        return json;
    }
}
