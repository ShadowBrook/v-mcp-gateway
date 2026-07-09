package com.mcpgateway.config;

import java.util.List;

public record AppConfig(
    int port,
    List<ServerConfig> servers,
    List<ToolConfig> tools,
    boolean trustAll,
    boolean blockInternal,
    List<String> corsOrigins
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
        if (corsOrigins == null || corsOrigins.isEmpty()) {
            corsOrigins = List.of("*");
        }
    }

    public AppConfig(int port, List<ServerConfig> servers, List<ToolConfig> tools) {
        this(port, servers, tools, false, true, List.of("*"));
    }

    public AppConfig(int port, List<ServerConfig> servers) {
        this(port, servers, List.of(), false, true, List.of("*"));
    }
}
