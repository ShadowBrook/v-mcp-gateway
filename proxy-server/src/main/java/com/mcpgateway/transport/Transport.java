package com.mcpgateway.transport;

import com.mcpgateway.domain.mcp.JsonRpcError;
import com.mcpgateway.domain.mcp.JsonRpcRequest;
import com.mcpgateway.domain.mcp.JsonRpcResponse;
import com.mcpgateway.domain.mcp.PromptSchema;
import com.mcpgateway.domain.mcp.ToolSchema;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public interface Transport {

    Logger log = LoggerFactory.getLogger(Transport.class);

    String name();

    String type();

    Future<Void> start();

    Future<Void> stop();

    Future<JsonObject> send(JsonObject request);

    /** Returns the backend session ID, or null if not available. */
    default String getSessionId() { return null; }

    /**
     * Sends a request and streams the backend response to the client.
     * Default implementation falls back to {@link #send} and writes JSON.
     */
    default Future<Void> sendStreaming(JsonObject request, HttpServerResponse clientResponse) {
        return send(request)
            .onSuccess(json -> {
                String sid = getSessionId();
                if (sid != null && !sid.isBlank()) {
                    clientResponse.putHeader("Mcp-Session-Id", sid);
                }
                clientResponse
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(json.encode());
            })
            .onFailure(err -> {
                log.error("Transport error for '{}': {}", name(), err.getMessage());
                JsonObject errorResponse = JsonRpcResponse.failure(
                    request.getValue("id"),
                    JsonRpcError.of(-32603, "Transport error: " + err.getMessage())
                ).toJson();
                clientResponse
                    .setStatusCode(502)
                    .putHeader("Content-Type", "application/json")
                    .end(errorResponse.encode());
            })
            .mapEmpty();
    }

    /**
     * Opens an SSE stream from the backend and pipes it to the client.
     * Used for GET-based SSE stream setup in Streamable HTTP.
     */
    default Future<Void> connectStream(HttpServerResponse clientResponse) {
        clientResponse.setStatusCode(405).end();
        return Future.succeededFuture();
    }

    /**
     * Fetches the tool list from the backend MCP server.
     */
    default Future<List<ToolSchema>> fetchTools() {
        var request = new JsonRpcRequest("2.0", 1, "tools/list", new JsonObject());
        return send(request.toJson()).map(response -> {
            List<ToolSchema> tools = new ArrayList<>();
            JsonObject result = response.getJsonObject("result");
            if (result != null) {
                JsonArray arr = result.getJsonArray("tools");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        tools.add(ToolSchema.from(arr.getJsonObject(i)));
                    }
                }
            }
            return tools;
        });
    }

    /**
     * Fetches the prompt list from the backend MCP server.
     */
    default Future<List<PromptSchema>> fetchPrompts() {
        var request = new JsonRpcRequest("2.0", 2, "prompts/list", new JsonObject());
        return send(request.toJson()).map(response -> {
            List<PromptSchema> prompts = new ArrayList<>();
            JsonObject result = response.getJsonObject("result");
            if (result != null) {
                JsonArray arr = result.getJsonArray("prompts");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        prompts.add(PromptSchema.from(arr.getJsonObject(i)));
                    }
                }
            }
            return prompts;
        });
    }

    /**
     * Fetches a specific prompt by name from the backend MCP server.
     */
    default Future<PromptSchema> fetchPrompt(String name) {
        var request = new JsonRpcRequest("2.0", 3, "prompts/get",
            new JsonObject().put("name", name));
        return send(request.toJson()).map(response -> {
            JsonObject result = response.getJsonObject("result");
            if (result != null) {
                return PromptSchema.from(result);
            }
            return null;
        });
    }

    boolean isRunning();
}
