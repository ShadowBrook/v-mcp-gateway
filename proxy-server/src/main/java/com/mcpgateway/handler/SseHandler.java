package com.mcpgateway.handler;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.service.StateManager;
import com.mcpgateway.transport.Transport;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SseHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(SseHandler.class);

    private final StateManager stateManager;

    public SseHandler(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String pathPrefix = ctx.pathParam("prefix");
        String queryPrefix = ctx.request().getParam("prefix");
        final String prefix = (pathPrefix != null && !pathPrefix.isBlank()) ? pathPrefix : queryPrefix;

        if (prefix == null || prefix.isBlank()) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing prefix parameter. Use /:prefix/sse or /sse?prefix=name").encode());
            return;
        }

        ServerConfig serverConfig = stateManager.getServerConfig(prefix);
        if (serverConfig == null) {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "No MCP server configured for prefix: " + prefix).encode());
            return;
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        HttpServerResponse response = ctx.response();

        response.setChunked(true);
        response.putHeader("Content-Type", "text/event-stream");
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("Connection", "keep-alive");
        response.setStatusCode(200);

        Transport transport = stateManager.getOrCreateTransport(prefix);
        if (transport == null) {
            response.setStatusCode(500);
            response.setChunked(false);
            response.putHeader("Content-Type", "application/json");
            response.end(new JsonObject().put("error", "Failed to create transport").encode());
            return;
        }

        transport.start()
            .onSuccess(v -> {
                stateManager.createSession(prefix, sessionId, transport, response)
                    .onSuccess(session -> {
                        String endpointUrl = "/" + prefix + "/message?sessionId=" + sessionId;
                        session.sendSse("endpoint", endpointUrl);
                        log.info("SSE session {} created for prefix '{}'", sessionId, prefix);

                        response.closeHandler(v2 -> {
                            log.info("SSE session {} closed", sessionId);
                            stateManager.removeSession(sessionId);
                            transport.stop();
                        });

                        response.exceptionHandler(err -> {
                            log.warn("SSE session {} error: {}", sessionId, err.getMessage());
                            stateManager.removeSession(sessionId);
                            transport.stop();
                        });
                    })
                    .onFailure(err -> {
                        log.error("Failed to create session: {}", err.getMessage());
                        response.setStatusCode(500).end();
                    });
            })
            .onFailure(err -> {
                log.error("Failed to start transport for prefix '{}': {}", prefix, err.getMessage());
                response.setStatusCode(502);
                response.setChunked(false);
                response.putHeader("Content-Type", "application/json");
                response.end(new JsonObject().put("error", "Failed to connect to backend: " + err.getMessage()).encode());
            });
    }
}
