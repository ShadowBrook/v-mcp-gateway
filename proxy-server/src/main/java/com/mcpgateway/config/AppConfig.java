package com.mcpgateway.config;

import java.util.List;

public record AppConfig(
    int port,
    List<ServerConfig> servers
) {
    public AppConfig {
        if (port <= 0) {
            port = 8080;
        }
        if (servers == null) {
            servers = List.of();
        }
    }
}
