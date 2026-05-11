package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Vertx;

public class TransportFactory {

    private final Vertx vertx;

    public TransportFactory(Vertx vertx) {
        this.vertx = vertx;
    }

    public Transport create(ServerConfig config) {
        return switch (config.type().toLowerCase()) {
            case "sse" -> new SseTransport(vertx, config);
            case "streamable-http", "streamable_http" -> new StreamableHttpTransport(vertx, config);
            case "stdio" -> new StdioTransport(vertx, config);
            default -> throw new IllegalArgumentException("Unknown transport type: " + config.type());
        };
    }
}
