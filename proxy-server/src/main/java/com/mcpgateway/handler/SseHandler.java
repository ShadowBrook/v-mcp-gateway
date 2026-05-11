package com.mcpgateway.handler;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.session.GatewaySession;
import com.mcpgateway.session.SessionStore;
import com.mcpgateway.transport.Transport;
import com.mcpgateway.transport.TransportFactory;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SseHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(SseHandler.class);

    private final TransportFactory transportFactory;
    private final SessionStore sessionStore;
    private final java.util.Map<String, ServerConfig> serverConfigs;

    public SseHandler(TransportFactory transportFactory, SessionStore sessionStore,
                      java.util.Map<String, ServerConfig> serverConfigs) {
        this.transportFactory = transportFactory;
        this.sessionStore = sessionStore;
        this.serverConfigs = serverConfigs;
    }

    @Override
    public void handle(RoutingContext ctx) {
        // Extract prefix from path param (/:prefix/sse) or query param (/sse?prefix=X)
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

        ServerConfig serverConfig = serverConfigs.get(prefix);
        if (serverConfig == null) {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "No MCP server configured for prefix: " + prefix).encode());
            return;
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        HttpServerResponse response = ctx.response();

        // Set up SSE response
        response.setChunked(true);
        response.putHeader("Content-Type", "text/event-stream");
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("Connection", "keep-alive");
        response.setStatusCode(200);

        // Create transport and start it
        Transport transport = transportFactory.create(serverConfig);

        transport.start()
            .onSuccess(v -> {
                // Create session with SSE response
                GatewaySession session = new GatewaySession(sessionId, prefix, transport, response);
                sessionStore.create(prefix, sessionId, session);

                // Send endpoint event to client
                String endpointUrl = "/" + prefix + "/message?sessionId=" + sessionId;
                session.sendSse("endpoint", endpointUrl);

                log.info("SSE session {} created for prefix '{}'", sessionId, prefix);

                // Handle client disconnect
                response.closeHandler(v2 -> {
                    log.info("SSE session {} closed", sessionId);
                    sessionStore.remove(sessionId);
                    transport.stop();
                });

                // Handle client error
                response.exceptionHandler(err -> {
                    log.warn("SSE session {} error: {}", sessionId, err.getMessage());
                    sessionStore.remove(sessionId);
                    transport.stop();
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
