package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public sealed interface ResourceContents
    permits TextResourceContents, BlobResourceContents {

    String uri();
    String mimeType();

    static ResourceContents from(JsonObject json) {
        String type = json.getString("type", "");
        if ("blob".equals(type)) {
            return BlobResourceContents.from(json);
        }
        return TextResourceContents.from(json);
    }
}
