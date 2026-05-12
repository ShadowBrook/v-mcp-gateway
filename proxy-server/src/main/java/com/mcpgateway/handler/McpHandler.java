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

public class McpHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(McpHandler.class);

    private final TransportFactory transportFactory;
    private final Map<String, ServerConfig> serverConfigs;
    private final Map<String, Transport> transports;

    public McpHandler(TransportFactory transportFactory, Map<String, ServerConfig> serverConfigs,
                      Map<String, Transport> transports) {
        this.transportFactory = transportFactory;
        this.serverConfigs = serverConfigs;
        this.transports = transports;
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

        // SSE-type servers must use GET /:prefix/sse for endpoint discovery;
        // POST /:prefix/mcp is only for Streamable HTTP servers.
        if ("sse".equals(serverConfig.type())) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "SSE endpoint discovery required — connect via GET /" + prefix + "/sse")
                    .encode());
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

            // Get or create transport lazily, keyed by prefix.
            // The transport manages its own backend session internally.
            Transport transport = transports.computeIfAbsent(prefix, k -> {
                Transport t = transportFactory.create(serverConfig);
                t.start().onFailure(err ->
                    log.error("Failed to start transport for '{}': {}", k, err.getMessage()));
                return t;
            });

            transport.sendStreaming(request, ctx.response());
        }).onFailure(err -> {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Failed to read request body").encode());
        });
    }
}
