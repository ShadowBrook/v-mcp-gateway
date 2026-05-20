package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record TextResourceContents(
    String uri,
    String mimeType,
    String text
) implements ResourceContents {

    public static TextResourceContents from(JsonObject json) {
        return new TextResourceContents(
            json.getString("uri", ""),
            json.getString("mimeType"),
            json.getString("text", ""));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("uri", uri)
            .put("type", "text")
            .put("text", text);
        if (mimeType != null) json.put("mimeType", mimeType);
        return json;
    }
}
