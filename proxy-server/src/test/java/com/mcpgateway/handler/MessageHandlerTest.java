package com.mcpgateway.handler;

import com.mcpgateway.service.StateManager;
import com.mcpgateway.session.impl.LocalSessionStore;
import com.mcpgateway.transport.Transport;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class MessageHandlerTest {

    private StateManager stateManager;
    private Transport mockTransport;
    private HttpServerResponse mockSseResponse;

    @BeforeEach
    void setUp(Vertx vertx) {
        mockTransport = mock(Transport.class);
        mockSseResponse = mock(HttpServerResponse.class);
        var sessionStore = new LocalSessionStore();
        stateManager = new StateManager(
            Collections.emptyMap(),
            Collections.emptyList(),
            null,
            sessionStore,
            vertx);
        stateManager.createSession("demo", "test-session", mockTransport, mockSseResponse);
    }

    @Test
    void shouldReturn404ForMissingSession(Vertx vertx, VertxTestContext testContext) {
        HttpServer testServer = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        Router router = Router.router(vertx);
        router.post("/:prefix/message").handler(new MessageHandler(stateManager));

        testServer.requestHandler(router).listen().onSuccess(server -> {
            WebClient client = WebClient.create(vertx);
            client.post(server.actualPort(), "localhost", "/demo/message")
                .addQueryParam("sessionId", "nonexistent")
                .sendJsonObject(new JsonObject().put("jsonrpc", "2.0").put("id", 1))
                .onSuccess(resp -> testContext.verify(() -> {
                    assertEquals(404, resp.statusCode());
                }))
                .onComplete(ar -> {
                    client.close();
                    server.close();
                    testContext.completeNow();
                });
        });
    }

    @Test
    void shouldReturn400ForMissingSessionId(Vertx vertx, VertxTestContext testContext) {
        HttpServer testServer = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        Router router = Router.router(vertx);
        router.post("/:prefix/message").handler(new MessageHandler(stateManager));

        testServer.requestHandler(router).listen().onSuccess(server -> {
            WebClient client = WebClient.create(vertx);
            client.post(server.actualPort(), "localhost", "/demo/message")
                .sendJsonObject(new JsonObject().put("jsonrpc", "2.0").put("id", 1))
                .onSuccess(resp -> testContext.verify(() -> {
                    assertEquals(400, resp.statusCode());
                }))
                .onComplete(ar -> {
                    client.close();
                    server.close();
                    testContext.completeNow();
                });
        });
    }

    @Test
    void shouldForwardRequestToTransport(Vertx vertx, VertxTestContext testContext) throws Exception {
        JsonObject expectedResponse = new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("result", new JsonObject().put("status", "ok"));

        when(mockTransport.send(any(JsonObject.class))).thenReturn(Future.succeededFuture(expectedResponse));

        HttpServer testServer = vertx.createHttpServer(new HttpServerOptions().setPort(0));
        Router router = Router.router(vertx);
        router.post("/:prefix/message").handler(new MessageHandler(stateManager));

        testServer.requestHandler(router).listen().onSuccess(server -> {
            WebClient client = WebClient.create(vertx);
            client.post(server.actualPort(), "localhost", "/demo/message")
                .addQueryParam("sessionId", "test-session")
                .sendJsonObject(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "test")
                    .put("params", new JsonObject()))
                .onSuccess(resp -> testContext.verify(() -> {
                    assertEquals(202, resp.statusCode());
                    verify(mockTransport).send(any(JsonObject.class));
                }))
                .onComplete(ar -> {
                    client.close();
                    server.close();
                    testContext.completeNow();
                });
        });
    }
}
