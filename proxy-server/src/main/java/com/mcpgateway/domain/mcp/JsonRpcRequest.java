package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record JsonRpcRequest(
    String jsonrpc,
    Object id,
    String method,
    JsonObject params
) {
    public static JsonRpcRequest from(JsonObject json) {
        return new JsonRpcRequest(
            json.getString("jsonrpc"),
            json.getValue("id"),
            json.getString("method"),
            json.getJsonObject("params", new JsonObject())
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("jsonrpc", jsonrpc)
            .put("method", method)
            .put("params", params);
        if (id != null) {
            json.put("id", id);
        }
        return json;
    }
}
