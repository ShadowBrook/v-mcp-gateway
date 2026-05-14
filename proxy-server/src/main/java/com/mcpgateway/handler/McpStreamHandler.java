package com.mcpgateway.handler;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.service.StateManager;
import com.mcpgateway.transport.Transport;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class McpStreamHandler implements Handler<RoutingContext> {

    private final StateManager stateManager;

    public McpStreamHandler(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String prefix = ctx.pathParam("prefix");
        ServerConfig serverConfig = stateManager.getServerConfig(prefix);
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

        Transport transport = stateManager.getOrCreateTransport(prefix);
        if (transport == null) {
            ctx.response().setStatusCode(500).end();
            return;
        }

        transport.connectStream(ctx.response());
    }
}
