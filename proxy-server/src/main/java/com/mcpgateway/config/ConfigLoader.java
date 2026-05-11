package com.mcpgateway.config;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    private final Vertx vertx;

    public ConfigLoader(Vertx vertx) {
        this.vertx = vertx;
    }

    public Future<AppConfig> load(String configPath) {
        return vertx.executeBlocking(() -> {
            try {
                String content;
                // Try classpath first, then file system
                InputStream is = getClass().getClassLoader().getResourceAsStream(configPath);
                if (is != null) {
                    content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    content = Files.readString(Path.of(configPath));
                }
                Yaml yaml = new Yaml();
                Map<String, Object> raw = yaml.load(content);

                // Parse server port
                int port = 8080;
                Object serverObj = raw.get("server");
                if (serverObj instanceof Map<?, ?> server) {
                    Object portVal = server.get("port");
                    if (portVal instanceof Integer p) {
                        port = p;
                    }
                }

                // Parse MCP servers
                List<ServerConfig> servers = new ArrayList<>();
                Object mcpObj = raw.get("mcp");
                if (mcpObj instanceof Map<?, ?> mcp) {
                    Object serversObj = mcp.get("servers");
                    if (serversObj instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> svr) {
                                servers.add(parseServerConfig(svr));
                            }
                        }
                    }
                }

                return new AppConfig(port, servers);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config from " + configPath, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private ServerConfig parseServerConfig(Map<?, ?> map) {
        String name = getString(map, "name", "unnamed");
        String type = getString(map, "type", "sse");
        String url = getString(map, "url", null);
        String command = getString(map, "command", null);
        List<String> args = getList(map, "args");
        Map<String, String> env = getMap(map, "env");
        int timeout = getInt(map, "timeout", 30_000);

        return new ServerConfig(name, type, url, command, args, env, timeout);
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getMap(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, String>) m;
        }
        return Map.of();
    }

    private String getString(Map<?, ?> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val instanceof String s ? s : defaultValue;
    }

    private int getInt(Map<?, ?> map, String key, int defaultValue) {
        Object val = map.get(key);
        return val instanceof Integer i ? i : defaultValue;
    }
}
