package com.mcpgateway.config;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadFullConfig() throws Exception {
        String yaml = """
            server:
              port: 9090
            mcp:
              servers:
                - name: sse-server
                  type: sse
                  url: http://localhost:8081/sse
                - name: stream-server
                  type: streamable-http
                  url: http://localhost:8082/mcp
                - name: stdio-server
                  type: stdio
                  command: echo
                  args:
                    - hello
                  env:
                    FOO: bar
                  timeout: 60000
            """;

        Path configFile = tempDir.resolve("test-config.yml");
        Files.writeString(configFile, yaml);

        Vertx vertx = Vertx.vertx();
        ConfigLoader loader = new ConfigLoader(vertx);

        AppConfig config = loader.load(configFile.toString())
            .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(9090, config.port());
        assertEquals(3, config.servers().size());

        ServerConfig sse = config.servers().get(0);
        assertEquals("sse-server", sse.name());
        assertTrue(sse.isSse());
        assertEquals("http://localhost:8081/sse", sse.url());

        ServerConfig stream = config.servers().get(1);
        assertEquals("stream-server", stream.name());
        assertTrue(stream.isStreamableHttp());

        ServerConfig stdio = config.servers().get(2);
        assertEquals("stdio-server", stdio.name());
        assertTrue(stdio.isStdio());
        assertEquals("echo", stdio.command());
        assertEquals(60000, stdio.timeout());
        assertEquals("bar", stdio.env().get("FOO"));

        vertx.close();
    }

    @Test
    void shouldUseDefaultsWhenMissingFields() throws Exception {
        String yaml = """
            server: {}
            mcp:
              servers:
                - name: default-server
                  type: sse
            """;

        Path configFile = tempDir.resolve("default-config.yml");
        Files.writeString(configFile, yaml);

        Vertx vertx = Vertx.vertx();
        ConfigLoader loader = new ConfigLoader(vertx);

        AppConfig config = loader.load(configFile.toString())
            .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(8080, config.port());
        assertEquals(1, config.servers().size());

        ServerConfig server = config.servers().get(0);
        assertEquals("default-server", server.name());
        assertEquals(30000, server.timeout());

        vertx.close();
    }
}
