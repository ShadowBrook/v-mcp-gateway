package com.mcpgateway.handler;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.transport.Transport;
import com.mcpgateway.transport.TransportFactory;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(McpHandler.class);

    private final TransportFactory transportFactory;
    private final Map<String, ServerConfig> serverConfigs;
    private final Map<String, Transport> transports = new ConcurrentHashMap<>();

    public McpHandler(TransportFactory transportFactory, Map<String, ServerConfig> serverConfigs) {
        this.transportFactory = transportFactory;
        this.serverConfigs = serverConfigs;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String prefix = ctx.pathParam("prefix");
        ServerConfig serverConfig = serverConfigs.get(prefix);
        if (serverConfig == null) {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "No MCP server configured for prefix: " + prefix).encode());
            return;
        }

        // Read request body
        ctx.request().body().onSuccess(body -> {
            JsonObject request;
            try {
                String bodyStr = body.toString();
                if (bodyStr.isBlank()) {
                    request = new JsonObject();
                } else {
                    request = new JsonObject(bodyStr);
                }
            } catch (Exception e) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Invalid JSON-RPC request").encode());
                return;
            }

            // Get or create transport lazily
            Transport transport = transports.computeIfAbsent(prefix, k -> {
                Transport t = transportFactory.create(serverConfig);
                t.start().onFailure(err ->
                    log.error("Failed to start transport for prefix '{}': {}", prefix, err.getMessage()));
                return t;
            });

            transport.send(request)
                .onSuccess(response -> {
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(err -> {
                    log.error("Transport error for prefix '{}': {}", prefix, err.getMessage());
                    JsonObject errorResponse = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", request.getValue("id"))
                        .put("error", new JsonObject()
                            .put("code", -32603)
                            .put("message", "Transport error: " + err.getMessage()));
                    ctx.response()
                        .setStatusCode(502)
                        .putHeader("Content-Type", "application/json")
                        .end(errorResponse.encode());
                });
        }).onFailure(err -> {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Failed to read request body").encode());
        });
    }
}
