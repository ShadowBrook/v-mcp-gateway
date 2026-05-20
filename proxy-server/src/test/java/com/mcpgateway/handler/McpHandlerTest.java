package com.mcpgateway.handler;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.config.ToolConfig;
import com.mcpgateway.domain.mcp.*;
import com.mcpgateway.service.StateManager;
import com.mcpgateway.session.impl.LocalSessionStore;
import com.mcpgateway.transport.Transport;
import com.mcpgateway.transport.TransportFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
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
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class McpHandlerTest {

    private StateManager stateManager;
    private Transport mockTransport;
    private TransportFactory mockTransportFactory;

    @BeforeEach
    void setUp(Vertx vertx) {
        mockTransport = mock(Transport.class);
        when(mockTransport.start()).thenReturn(Future.succeededFuture());
        when(mockTransport.isRunning()).thenReturn(true);
        when(mockTransport.getSessionId()).thenReturn(null);

        mockTransportFactory = mock(TransportFactory.class);
        when(mockTransportFactory.create(any())).thenReturn(mockTransport);

        Map<String, ServerConfig> servers = Map.of(
            "streamable-demo", new ServerConfig("demo", "streamable-http", "http://localhost:9999/mcp",
                null, null, null, 30_000));

        stateManager = new StateManager(
            servers,
            Collections.emptyList(),
            mockTransportFactory,
            new LocalSessionStore(),
            vertx);
    }

    // ---- resources/list ----

    @Test
    void shouldReturnResourcesList(Vertx vertx, VertxTestContext ctx) {
        List<ResourceSchema> resources = List.of(
            new ResourceSchema("file:///doc.txt", "doc", "A document", "text/plain", 1024L, null),
            new ResourceSchema("file:///img.png", "img", "An image", "image/png", null, null));

        when(mockTransport.fetchResources()).thenReturn(Future.succeededFuture(resources));

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "resources/list"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    JsonArray arr = body.getJsonObject("result").getJsonArray("resources");
                    assertEquals(2, arr.size());
                    assertEquals("file:///doc.txt", arr.getJsonObject(0).getString("uri"));
                    assertEquals("doc", arr.getJsonObject(0).getString("name"));
                    assertEquals("text/plain", arr.getJsonObject(0).getString("mimeType"));
                    assertEquals(1024, arr.getJsonObject(0).getInteger("size"));
                    assertEquals("file:///img.png", arr.getJsonObject(1).getString("uri"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    @Test
    void shouldReturnErrorWhenResourcesListFails(Vertx vertx, VertxTestContext ctx) {
        when(mockTransport.fetchResources())
            .thenReturn(Future.failedFuture("Connection refused"));

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 2)
                    .put("method", "resources/list"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertNotNull(body.getJsonObject("error"));
                    assertEquals(-32603, body.getJsonObject("error").getInteger("code"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- resources/read ----

    @Test
    void shouldReturnResourceContents(Vertx vertx, VertxTestContext ctx) {
        List<ResourceContents> contents = List.of(
            new TextResourceContents("file:///doc.txt", "text/plain", "Hello, World!"));

        when(mockTransport.fetchResource("file:///doc.txt"))
            .thenReturn(Future.succeededFuture(contents));

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 3)
                    .put("method", "resources/read")
                    .put("params", new JsonObject()
                        .put("uri", "file:///doc.txt")))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    JsonArray contentsArr = body.getJsonObject("result").getJsonArray("contents");
                    assertEquals(1, contentsArr.size());
                    JsonObject c = contentsArr.getJsonObject(0);
                    assertEquals("text", c.getString("type"));
                    assertEquals("file:///doc.txt", c.getString("uri"));
                    assertEquals("Hello, World!", c.getString("text"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    @Test
    void shouldReturnErrorForMissingUri(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 4)
                    .put("method", "resources/read")
                    .put("params", new JsonObject()))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    assertNotNull(body.getJsonObject("error"));
                    assertEquals(-32602, body.getJsonObject("error").getInteger("code"));
                    assertTrue(body.getJsonObject("error").getString("message")
                        .contains("Missing resource uri"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- resources/templates/list ----

    @Test
    void shouldReturnResourceTemplatesList(Vertx vertx, VertxTestContext ctx) {
        List<ResourceTemplate> templates = List.of(
            new ResourceTemplate("file:///{path}", "Files", "Access files", null),
            new ResourceTemplate("db:///{table}", "Database", "Access database tables", "application/json"));

        when(mockTransport.fetchResourceTemplates())
            .thenReturn(Future.succeededFuture(templates));

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 5)
                    .put("method", "resources/templates/list"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(200, resp.statusCode());
                    JsonObject body = resp.bodyAsJsonObject();
                    JsonArray arr = body.getJsonObject("result").getJsonArray("resourceTemplates");
                    assertEquals(2, arr.size());
                    assertEquals("file:///{path}", arr.getJsonObject(0).getString("uriTemplate"));
                    assertEquals("Files", arr.getJsonObject(0).getString("name"));
                    assertEquals("db:///{table}", arr.getJsonObject(1).getString("uriTemplate"));
                    assertEquals("application/json", arr.getJsonObject(1).getString("mimeType"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- unknown method forwards to transport ----

    @Test
    void shouldForwardUnknownMethodToTransport(Vertx vertx, VertxTestContext ctx) {
        doAnswer(inv -> {
            HttpServerResponse resp = inv.getArgument(1);
            resp.setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end("{}");
            return Future.succeededFuture();
        }).when(mockTransport).sendStreaming(any(JsonObject.class), any());

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 6)
                    .put("method", "some/unknown/method")
                    .put("params", new JsonObject()))
                .onSuccess(resp -> ctx.verify(() -> {
                    verify(mockTransport).sendStreaming(any(JsonObject.class), any());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- completion/complete forwards to transport ----

    @Test
    void shouldForwardCompletionCompleteToTransport(Vertx vertx, VertxTestContext ctx) {
        doAnswer(inv -> {
            HttpServerResponse resp = inv.getArgument(1);
            resp.setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end("{}");
            return Future.succeededFuture();
        }).when(mockTransport).sendStreaming(any(JsonObject.class), any());

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 7)
                    .put("method", "completion/complete")
                    .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                            .put("type", "ref/resource")
                            .put("uri", "file:///doc.txt"))
                        .put("argument", new JsonObject()
                            .put("name", "path")
                            .put("value", "/home"))))
                .onSuccess(resp -> ctx.verify(() -> {
                    verify(mockTransport).sendStreaming(any(JsonObject.class), any());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- sampling/createMessage forwards to transport ----

    @Test
    void shouldForwardSamplingCreateMessageToTransport(Vertx vertx, VertxTestContext ctx) {
        doAnswer(inv -> {
            HttpServerResponse resp = inv.getArgument(1);
            resp.setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end("{}");
            return Future.succeededFuture();
        }).when(mockTransport).sendStreaming(any(JsonObject.class), any());

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/streamable-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 8)
                    .put("method", "sampling/createMessage")
                    .put("params", new JsonObject()
                        .put("messages", new JsonArray()
                            .add(new JsonObject()
                                .put("role", "user")
                                .put("content", new JsonObject()
                                    .put("type", "text")
                                    .put("text", "Hello"))))
                        .put("maxTokens", 100)))
                .onSuccess(resp -> ctx.verify(() -> {
                    verify(mockTransport).sendStreaming(any(JsonObject.class), any());
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- 404 for unknown prefix ----

    @Test
    void shouldReturn404ForUnknownPrefix(Vertx vertx, VertxTestContext ctx) {
        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(stateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/unknown-prefix/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "tools/list"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(404, resp.statusCode());
                    assertTrue(resp.bodyAsJsonObject().getString("error")
                        .contains("No MCP server configured"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- 400 for SSE server on POST /mcp ----

    @Test
    void shouldReturn400ForSseServerOnPostMcp(Vertx vertx, VertxTestContext ctx) {
        Map<String, ServerConfig> sseServers = Map.of(
            "sse-demo", new ServerConfig("sse-demo", "sse", "http://localhost:9999/sse",
                null, null, null, 30_000));

        StateManager sseStateManager = new StateManager(
            sseServers,
            Collections.emptyList(),
            mockTransportFactory,
            new LocalSessionStore(),
            vertx);

        startServer(vertx, router -> {
            router.post("/:prefix/mcp").handler(new McpHandler(sseStateManager));
        }).onSuccess(port -> {
            WebClient client = WebClient.create(vertx);
            client.post(port, "localhost", "/sse-demo/mcp")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "tools/list"))
                .onSuccess(resp -> ctx.verify(() -> {
                    assertEquals(400, resp.statusCode());
                    assertTrue(resp.bodyAsJsonObject().getString("error")
                        .contains("SSE endpoint discovery"));
                }))
                .onComplete(ar -> {
                    client.close();
                    ctx.completeNow();
                });
        });
    }

    // ---- helper ----

    private Future<Integer> startServer(Vertx vertx,
                                                       java.util.function.Consumer<Router> routerSetup) {
        Promise<Integer> promise = Promise.promise();
        HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        Router router = Router.router(vertx);
        routerSetup.accept(router);
        server.requestHandler(router).listen()
            .onSuccess(s -> promise.complete(s.actualPort()))
            .onFailure(promise::fail);
        return promise.future();
    }
}
