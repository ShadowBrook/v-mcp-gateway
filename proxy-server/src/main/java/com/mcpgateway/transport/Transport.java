package com.mcpgateway.transport;

import com.mcpgateway.domain.mcp.JsonRpcError;
import com.mcpgateway.domain.mcp.JsonRpcResponse;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    boolean isRunning();
}
