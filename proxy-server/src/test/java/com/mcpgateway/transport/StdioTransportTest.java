package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class StdioTransportTest {

    @Test
    void shouldStartAndStopStdioProcess(Vertx vertx, VertxTestContext testContext) {
        // Use 'cat' as a simple stdio process that echoes input
        ServerConfig config = new ServerConfig("test-stdio", "stdio",
            null, "cat", List.of(), java.util.Map.of(), 5000);

        StdioTransport transport = new StdioTransport(vertx, config);

        transport.start()
            .onSuccess(v -> testContext.verify(() -> {
                assertTrue(transport.isRunning());
            }))
            .compose(v -> transport.stop())
            .onSuccess(v -> {
                assertFalse(transport.isRunning());
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void shouldSendAndReceiveResponse(Vertx vertx, VertxTestContext testContext) throws Exception {
        // Use a bash script that reads JSON-RPC from stdin and echoes a response
        String script = """
            #!/bin/bash
            read line
            echo '{"jsonrpc":"2.0","id":1,"result":{"status":"ok"}}'
            """;

        java.nio.file.Path scriptFile = java.nio.file.Files.createTempFile("mcp-test", ".sh");
        java.nio.file.Files.writeString(scriptFile, script);
        scriptFile.toFile().setExecutable(true);

        ServerConfig config = new ServerConfig("test-stdio", "stdio",
            null, scriptFile.toString(), List.of(), java.util.Map.of(), 5000);

        StdioTransport transport = new StdioTransport(vertx, config);

        transport.start()
            .onSuccess(v -> {
                JsonObject request = new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "test")
                    .put("params", new JsonObject());

                transport.send(request)
                    .onSuccess(response -> testContext.verify(() -> {
                        assertEquals("2.0", response.getString("jsonrpc"));
                        assertEquals(1, response.getValue("id"));
                        assertNotNull(response.getJsonObject("result"));
                        assertEquals("ok", response.getJsonObject("result").getString("status"));
                    }))
                    .onComplete(ar -> {
                        transport.stop().onComplete(v2 -> {
                            scriptFile.toFile().delete();
                            testContext.completeNow();
                        });
                    })
                    .onFailure(err -> {
                        transport.stop();
                        scriptFile.toFile().delete();
                        testContext.failNow(err);
                    });
            })
            .onFailure(err -> {
                scriptFile.toFile().delete();
                testContext.failNow(err);
            });
    }

    @Test
    void shouldHandleNotification(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-stdio", "stdio",
            null, "cat", List.of(), java.util.Map.of(), 5000);

        StdioTransport transport = new StdioTransport(vertx, config);

        transport.start()
            .onSuccess(v -> {
                // Notification has no id - should complete immediately
                JsonObject notification = new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("method", "notifications/initialized");

                transport.send(notification)
                    .onSuccess(response -> testContext.verify(() -> {
                        // Response should be an empty object
                        assertEquals(new JsonObject(), response);
                    }))
                    .onComplete(ar -> {
                        transport.stop();
                        testContext.completeNow();
                    })
                    .onFailure(testContext::failNow);
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void shouldFailWhenSendBeforeStart(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-stdio", "stdio",
            null, "cat", List.of(), java.util.Map.of(), 5000);

        StdioTransport transport = new StdioTransport(vertx, config);

        JsonObject request = new JsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", "test");
        transport.send(request)
            .onSuccess(r -> testContext.failNow("Should have failed"))
            .onFailure(err -> testContext.verify(() -> {
                assertTrue(err.getMessage().contains("not running"));
                testContext.completeNow();
            }));
    }
}
