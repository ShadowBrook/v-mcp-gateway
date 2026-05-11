package com.mcpgateway.config;

import java.util.List;

public record ServerConfig(
    String name,
    String type,
    String url,
    String command,
    List<String> args,
    java.util.Map<String, String> env,
    int timeout
) {
    public ServerConfig {
        if (type == null || type.isBlank()) {
            type = "sse";
        }
        if (timeout <= 0) {
            timeout = 30_000;
        }
    }

    public boolean isSse() {
        return "sse".equalsIgnoreCase(type);
    }

    public boolean isStreamableHttp() {
        return "streamable-http".equalsIgnoreCase(type) || "streamable_http".equalsIgnoreCase(type);
    }

    public boolean isStdio() {
        return "stdio".equalsIgnoreCase(type);
    }
}
