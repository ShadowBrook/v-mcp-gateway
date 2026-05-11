package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonObject;

public record JsonRpcResponse(
    String jsonrpc,
    Object id,
    JsonObject result,
    JsonRpcError error
) {
    public boolean isError() {
        return error != null;
    }

    public static JsonRpcResponse success(Object id, JsonObject result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse failure(Object id, JsonRpcError error) {
        return new JsonRpcResponse("2.0", id, null, error);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject().put("jsonrpc", jsonrpc);
        if (id != null) {
            json.put("id", id);
        }
        if (result != null) {
            json.put("result", result);
        }
        if (error != null) {
            json.put("error", error.toJson());
        }
        return json;
    }

    public static JsonRpcResponse from(JsonObject json) {
        JsonObject errorJson = json.getJsonObject("error");
        JsonRpcError error = errorJson != null ? JsonRpcError.from(errorJson) : null;
        return new JsonRpcResponse(
            json.getString("jsonrpc"),
            json.getValue("id"),
            json.getJsonObject("result"),
            error
        );
    }
}
