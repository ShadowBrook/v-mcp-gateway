package com.mcpgateway.config;

import java.util.List;

public record AppConfig(
    int port,
    List<ServerConfig> servers,
    List<ToolConfig> tools
) {
    public AppConfig {
        if (port <= 0) {
            port = 8080;
        }
        if (servers == null) {
            servers = List.of();
        }
        if (tools == null) {
            tools = List.of();
        }
    }

    public AppConfig(int port, List<ServerConfig> servers) {
        this(port, servers, List.of());
    }
}
