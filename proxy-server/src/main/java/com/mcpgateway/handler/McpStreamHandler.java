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

/**
 * Handles GET /:prefix/mcp — establishes an SSE stream from backend to browser.
 * This is the receiving channel for Streamable HTTP, analogous to GET /:prefix/sse for SSE.
 */
public class McpStreamHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(McpStreamHandler.class);

    private final TransportFactory transportFactory;
    private final Map<String, ServerConfig> serverConfigs;
    private final Map<String, Transport> transports;

    public McpStreamHandler(TransportFactory transportFactory, Map<String, ServerConfig> serverConfigs,
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

        if (!serverConfig.isStreamableHttp()) {
            ctx.response().setStatusCode(405).end();
            return;
        }

        Transport transport = transports.computeIfAbsent(prefix, k ->
            transportFactory.create(serverConfig));

        transport.connectStream(ctx.response());
    }
}
