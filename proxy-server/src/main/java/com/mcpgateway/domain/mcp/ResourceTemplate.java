package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record ResourceTemplate(
    String uriTemplate,
    String name,
    String description,
    String mimeType
) {
    public static ResourceTemplate from(JsonObject json) {
        return new ResourceTemplate(
            json.getString("uriTemplate", ""),
            json.getString("name", ""),
            json.getString("description", ""),
            json.getString("mimeType"));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("uriTemplate", uriTemplate)
            .put("name", name);
        if (description != null) json.put("description", description);
        if (mimeType != null) json.put("mimeType", mimeType);
        return json;
    }
}
