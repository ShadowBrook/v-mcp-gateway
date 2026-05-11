package com.mcpgateway.handler;

import com.mcpgateway.domain.mcp.JsonRpcError;
import com.mcpgateway.domain.mcp.JsonRpcResponse;
import com.mcpgateway.session.SessionStore;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);

    private final SessionStore sessionStore;

    public MessageHandler(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String sessionId = ctx.request().getParam("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing sessionId parameter").encode());
            return;
        }

        sessionStore.get(sessionId)
            .onSuccess(session -> {
                // Read request body
                ctx.request().body().onSuccess(body -> {
                    JsonObject request;
                    try {
                        request = new JsonObject(body.toString());
                    } catch (Exception e) {
                        ctx.response()
                            .setStatusCode(400)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Invalid JSON-RPC request").encode());
                        return;
                    }

                    // Forward to transport
                    session.transport().send(request)
                        .onSuccess(response -> {
                            // Send response back to client via SSE
                            if (response.containsKey("jsonrpc")) {
                                session.sendSse("message", response.encode());
                            }
                            // Acknowledge HTTP request
                            ctx.response()
                                .setStatusCode(202)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().encode());
                        })
                        .onFailure(err -> {
                            log.error("Transport error for session {}: {}", sessionId, err.getMessage());
                            JsonObject errorResponse = JsonRpcResponse.failure(
                                request.getValue("id"),
                                JsonRpcError.of(-32603, "Transport error: " + err.getMessage())
                            ).toJson();
                            session.sendSse("message", errorResponse.encode());
                            ctx.response()
                                .setStatusCode(202)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().encode());
                        });
                }).onFailure(err -> {
                    ctx.response()
                        .setStatusCode(400)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", "Failed to read request body").encode());
                });
            })
            .onFailure(err -> {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Session not found: " + sessionId).encode());
            });
    }
}
