package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record BlobResourceContents(
    String uri,
    String mimeType,
    String blob
) implements ResourceContents {

    public static BlobResourceContents from(JsonObject json) {
        return new BlobResourceContents(
            json.getString("uri", ""),
            json.getString("mimeType"),
            json.getString("blob", ""));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("uri", uri)
            .put("type", "blob")
            .put("blob", blob);
        if (mimeType != null) json.put("mimeType", mimeType);
        return json;
    }
}
