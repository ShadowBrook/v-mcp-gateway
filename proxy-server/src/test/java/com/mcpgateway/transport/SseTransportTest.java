package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class SseTransportTest {

    private HttpServer mockBackend;
    private int backendPort;

    @BeforeEach
    void setUp(Vertx vertx) throws Exception {
        AtomicReference<HttpServerResponse> sseResponse = new AtomicReference<>();

        mockBackend = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        mockBackend.requestHandler(req -> {
            if (req.path().equals("/sse")) {
                // Open SSE connection
                HttpServerResponse resp = req.response();
                resp.setChunked(true);
                resp.putHeader("Content-Type", "text/event-stream");
                resp.putHeader("Cache-Control", "no-cache");
                sseResponse.set(resp);

                // Send endpoint event
                resp.write("event: endpoint\ndata: /message?sessionId=backend-123\n\n");
            } else if (req.path().startsWith("/message")) {
                req.body().onSuccess(body -> {
                    JsonObject request = new JsonObject(body.toString());
                    JsonObject response = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", request.getValue("id"))
                        .put("result", new JsonObject()
                            .put("protocolVersion", "2024-11-05")
                            .put("capabilities", new JsonObject()
                                .put("tools", new JsonObject())));

                    // Write response back to the SSE stream
                    HttpServerResponse sseResp = sseResponse.get();
                    if (sseResp != null) {
                        sseResp.write("event: message\ndata: " + response.encode() + "\n\n");
                    }

                    // Also acknowledge the POST
                    req.response().end();
                });
            }
        });

        mockBackend.listen()
            .onSuccess(s -> backendPort = s.actualPort())
            .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (mockBackend != null) {
            mockBackend.close();
        }
    }

    @Test
    void shouldConnectAndStart(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-sse", "sse",
            "http://localhost:" + backendPort + "/sse", null, null, null, 5000);

        SseTransport transport = new SseTransport(vertx, config);

        transport.start()
            .onSuccess(v -> testContext.verify(() -> {
                assertTrue(transport.isRunning());
            }))
            .onComplete(ar -> {
                transport.stop();
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void shouldSendRequestAndReceiveResponse(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-sse", "sse",
            "http://localhost:" + backendPort + "/sse", null, null, null, 10000);

        SseTransport transport = new SseTransport(vertx, config);

        transport.start()
            .onSuccess(v -> {
                // Wait a bit for the endpoint event to arrive
                vertx.setTimer(300, id -> {
                    JsonObject request = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", 1)
                        .put("method", "initialize")
                        .put("params", new JsonObject());

                    transport.send(request)
                        .onSuccess(response -> testContext.verify(() -> {
                            assertNotNull(response);
                            assertEquals("2.0", response.getString("jsonrpc"));
                            assertEquals(1, response.getValue("id"));
                            assertNotNull(response.getJsonObject("result"));
                            assertTrue(response.getJsonObject("result")
                                .getJsonObject("capabilities").containsKey("tools"));
                        }))
                        .onComplete(ar -> {
                            transport.stop();
                            testContext.completeNow();
                        })
                        .onFailure(testContext::failNow);
                });
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void shouldFailWhenNotRunning(Vertx vertx, VertxTestContext testContext) {
        ServerConfig config = new ServerConfig("test-sse", "sse",
            "http://localhost:" + backendPort + "/sse", null, null, null, 5000);

        SseTransport transport = new SseTransport(vertx, config);

        JsonObject request = new JsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", "test");
        transport.send(request)
            .onSuccess(r -> testContext.failNow("Should have failed"))
            .onFailure(err -> testContext.verify(() -> {
                assertTrue(err.getMessage().contains("not running"));
                testContext.completeNow();
            }));
    }
}
