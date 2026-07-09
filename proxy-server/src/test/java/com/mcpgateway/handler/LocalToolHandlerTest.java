package com.mcpgateway.handler;

import com.mcpgateway.config.ToolConfig;
import com.mcpgateway.service.StateManager;
import com.mcpgateway.session.impl.LocalSessionStore;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class LocalToolHandlerTest {

    private StateManager stateManager;
    private ToolConfig testTool;

    @BeforeEach
    void setUp(Vertx vertx) {
        testTool = new ToolConfig(
            "testTool",
            "A test tool for unit testing",
            "GET",
            "http://localhost:{port}/api/test-tool",
            null,
            null,
            new JsonObject()
                .put("type", "object")
                .put("properties", new JsonObject()
                    .put("name", new JsonObject()
                        .put("type", "string"))));

        stateManager = new StateManager(
            Collections.emptyMap(),
            List.of(testTool),
            null,
            new LocalSessionStore(),
            vertx,
            false,  // trustAll
            false); // blockInternal — allow localhost for test
    }

    // ---- initialize ----

    @Test
    void shouldReturnServerInfoOnInitialize(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "initialize")
                    .put("params", new JsonObject()
                        .put("protocolVersion", "2025-11-25")
                        .put("capabilities", new JsonObject())
                        .put("clientInfo", new JsonObject()
                            .put("name", "test")
                            .put("version", "1.0"))))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertEquals("2.0", body.getString("jsonrpc"));
                    assertEquals(1, body.getInteger("id"));
                    JsonObject result = body.getJsonObject("result");
                    assertEquals("2025-11-25", result.getString("protocolVersion"));
                    assertEquals("mcp-gateway-local",
                        result.getJsonObject("serverInfo").getString("name"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- tools/list ----

    @Test
    void shouldListConfiguredTools(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 2)
                    .put("method", "tools/list"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    JsonArray tools = body.getJsonObject("result").getJsonArray("tools");
                    assertEquals(1, tools.size());
                    JsonObject tool = tools.getJsonObject(0);
                    assertEquals("testTool", tool.getString("name"));
                    assertEquals("A test tool for unit testing", tool.getString("description"));
                    assertNotNull(tool.getJsonObject("inputSchema"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- tools/call ----

    @Test
    void shouldExecuteLocalTool(Vertx vertx, VertxTestContext ctx) {
        // Set up a backend HTTP server for the tool to call
        HttpServer backend = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        Router backendRouter = Router.router(vertx);
        backendRouter.get("/api/test-tool").handler(rc ->
            rc.response().putHeader("Content-Type", "application/json")
                .end("{\"result\":\"success\"}"));
        backend.requestHandler(backendRouter).listen().onSuccess(backendServer -> {
            int backendPort = backendServer.actualPort();

            startServer(vertx, router -> {
                router.post("/mcp").handler(new LocalToolHandler(stateManager));
            }).onSuccess(gatewayPort -> {
                WebClient client = WebClient.create(vertx);
                client.post(gatewayPort, "localhost", "/mcp")
                    .sendJsonObject(new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", 3)
                        .put("method", "tools/call")
                        .put("params", new JsonObject()
                            .put("name", "testTool")
                            .put("arguments", new JsonObject()
                                .put("port", backendPort)
                                .put("name", "test"))))
                    .onSuccess(resp -> ctx.verify(() -> {
                        assertEquals(200, resp.statusCode());
                        JsonObject body = resp.bodyAsJsonObject();
                        JsonArray content = body.getJsonObject("result").getJsonArray("content");
                        assertEquals(1, content.size());
                        assertEquals("text", content.getJsonObject(0).getString("type"));
                        assertTrue(content.getJsonObject(0).getString("text")
                            .contains("success"));
                        assertFalse(body.getJsonObject("result").getBoolean("isError"));
                    }))
                    .onComplete(ar -> {
                        client.close();
                        backendServer.close();
                        ctx.completeNow();
                    });
            });
        });
    }

    @Test
    void shouldReturnErrorForUnknownTool(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 4)
                    .put("method", "tools/call")
                    .put("params", new JsonObject()
                        .put("name", "nonexistent")
                        .put("arguments", new JsonObject())))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertNotNull(body.getJsonObject("error"));
                    assertEquals(-32602, body.getJsonObject("error").getInteger("code"));
                    assertTrue(body.getJsonObject("error").getString("message")
                        .contains("Unknown tool"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- ping ----

    @Test
    void shouldRespondToPingWithId(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 5)
                    .put("method", "ping"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertEquals("2.0", body.getString("jsonrpc"));
                    assertEquals(5, body.getInteger("id"));
                    assertNotNull(body.getJsonObject("result"));
                    assertEquals(0, body.getJsonObject("result").size());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    @Test
    void shouldRespondToPingWithoutId(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("method", "ping"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(202, resp.statusCode());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- notifications/initialized ----

    @Test
    void shouldAckInitializedNotification(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("method", "notifications/initialized"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(202, resp.statusCode());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- unknown method ----

    @Test
    void shouldReturnErrorForUnknownMethod(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 7)
                    .put("method", "nonexistent/method"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertNotNull(body.getJsonObject("error"));
                    assertEquals(-32601, body.getJsonObject("error").getInteger("code"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- invalid body ----

    @Test
    void shouldReturn400ForInvalidJson(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .putHeader("Content-Type", "application/json")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer("not json"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(400, resp.statusCode());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    @Test
    void shouldHandleEmptyBody(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/mcp").handler(new LocalToolHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/mcp")
                .sendJsonObject(new JsonObject())
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertNotNull(body.getJsonObject("error"));
                    assertEquals(-32601, body.getJsonObject("error").getInteger("code"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- helper ----

    private io.vertx.core.Future<Integer> startServer(Vertx vertx,
                                                       java.util.function.Consumer<Router> routerSetup) {
        io.vertx.core.Promise<Integer> promise = io.vertx.core.Promise.promise();
        HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        Router router = Router.router(vertx);
        routerSetup.accept(router);
        server.requestHandler(router).listen()
            .onSuccess(s -> promise.complete(s.actualPort()))
            .onFailure(promise::fail);
        return promise.future();
    }
}
