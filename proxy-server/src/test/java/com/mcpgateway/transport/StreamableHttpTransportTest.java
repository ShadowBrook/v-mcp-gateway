package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class StreamableHttpTransportTest {

    private HttpServer mockBackend;
    private int backendPort;

    @BeforeEach
    void setUp(Vertx vertx) throws Exception {
        mockBackend = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        mockBackend.requestHandler(req -> {
            req.body().onSuccess(body -> {
                JsonObject request = new JsonObject(body.toString());
                String method = request.getString("method");

                JsonObject response = new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", request.getValue("id"));

                if ("initialize".equals(method)) {
                    response.put("result", new JsonObject()
                        .put("protocolVersion", "2024-11-05")
                        .put("serverInfo", new JsonObject()
                            .put("name", "test-server")
                            .put("version", "1.0")));
                } else if ("tools/list".equals(method)) {
                    response.put("result", new JsonObject()
                        .put("tools", new io.vertx.core.json.JsonArray()
                            .add(new JsonObject()
                                .put("name", "tool1")
                                .put("description", "First tool"))));
                }

                req.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode());
            });
        });

        mockBackend.listen()
            .onSuccess(s -> backendPort = s.actualPort())
            .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldForwardRequestAndReturnResponse(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-stream", "streamable-http",
            "http://localhost:" + backendPort + "/mcp", null, null, null, 5000);

        StreamableHttpTransport transport = new StreamableHttpTransport(vertx, config);

        JsonObject request = new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "initialize")
            .put("params", new JsonObject());

        transport.send(request)
            .onSuccess(response -> testContext.verify(() -> {
                assertEquals("2.0", response.getString("jsonrpc"));
                assertEquals(1, response.getValue("id"));
                assertNotNull(response.getJsonObject("result"));
                assertEquals("2024-11-05",
                    response.getJsonObject("result").getString("protocolVersion"));
            }))
            .onComplete(ar -> {
                transport.stop();
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void shouldHandleToolListRequest(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-stream", "streamable-http",
            "http://localhost:" + backendPort + "/mcp", null, null, null, 5000);

        StreamableHttpTransport transport = new StreamableHttpTransport(vertx, config);

        JsonObject request = new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 2)
            .put("method", "tools/list")
            .put("params", new JsonObject());

        transport.send(request)
            .onSuccess(response -> testContext.verify(() -> {
                assertTrue(response.getJsonObject("result").containsKey("tools"));
            }))
            .onComplete(ar -> {
                transport.stop();
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void shouldFailWithBadUrl(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("bad-config", "streamable-http",
            "http://nonexistent-host:9999/mcp", null, null, null, 1000);

        StreamableHttpTransport transport = new StreamableHttpTransport(vertx, config);

        JsonObject request = new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "test");

        transport.send(request)
            .onSuccess(r -> testContext.failNow("Should have failed"))
            .onFailure(err -> testContext.verify(() -> {
                assertNotNull(err.getMessage());
                testContext.completeNow();
            }));
    }
}
