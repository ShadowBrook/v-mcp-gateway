package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record JsonRpcError(
    int code,
    String message,
    Object data
) {
    public static JsonRpcError of(int code, String message, Object data) {
        return new JsonRpcError(code, message, data);
    }

    public static JsonRpcError of(int code, String message) {
        return new JsonRpcError(code, message, null);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("code", code)
            .put("message", message);
        if (data != null) {
            json.put("data", data);
        }
        return json;
    }

    public static JsonRpcError from(JsonObject json) {
        return new JsonRpcError(
            json.getInteger("code", -1),
            json.getString("message", ""),
            json.getValue("data")
        );
    }
}
