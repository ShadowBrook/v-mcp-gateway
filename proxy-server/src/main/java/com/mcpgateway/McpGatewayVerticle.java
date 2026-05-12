package com.mcpgateway;

import com.mcpgateway.config.AppConfig;
import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.handler.*;
import com.mcpgateway.session.LocalSessionStore;
import com.mcpgateway.session.SessionStore;
import com.mcpgateway.transport.Transport;
import com.mcpgateway.transport.TransportFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpGatewayVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayVerticle.class);

    private final AppConfig appConfig;
    private final Map<String, ServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final Map<String, Transport> sseTransports = new ConcurrentHashMap<>();
    private TransportFactory transportFactory;
    private SessionStore sessionStore;

    public McpGatewayVerticle(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        transportFactory = new TransportFactory(vertx);
        sessionStore = new LocalSessionStore();

        // Build server config map by name
        for (ServerConfig sc : appConfig.servers()) {
            serverConfigs.put(sc.name(), sc);
        }

        Router router = setupRouter();

        int port = appConfig.port();
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(server -> {
                log.info("MCP Gateway listening on port {}", server.actualPort());
                // Start transports that need pre-starting (e.g., stdio)
                startTransports();
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private Router setupRouter() {
        Router router = Router.router(vertx);

        // CORS — allow all for development
        router.route().handler(CorsHandler.create("*")
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.OPTIONS)
            .allowedMethod(HttpMethod.DELETE)
            .allowedHeader("*")
            .exposedHeader("Mcp-Session-Id")
            .exposedHeader("Content-Type"));

        // Global middleware: log requests
        router.route().handler(ctx -> {
            log.debug("{} {} from {}", ctx.request().method(), ctx.request().uri(), ctx.request().remoteAddress());
            ctx.next();
        });

        // Health check
        router.get("/health_check").handler(HealthHandler.create());

        // SSE endpoints: prefix in path (:prefix/sse) or query param (/sse?prefix=X)
        SseHandler sseHandler = new SseHandler(transportFactory, sessionStore, serverConfigs);
        router.get("/:prefix/sse").handler(sseHandler);
        router.get("/sse").handler(sseHandler);

        // Message endpoint
        router.post("/:prefix/message").handler(new MessageHandler(sessionStore));

        // Streamable HTTP endpoints: GET for SSE receive stream, POST for sending
        Map<String, Transport> mcpTransports = new ConcurrentHashMap<>();
        router.get("/:prefix/mcp").handler(new McpStreamHandler(transportFactory, serverConfigs, mcpTransports));
        router.post("/:prefix/mcp").handler(new McpHandler(transportFactory, serverConfigs, mcpTransports));

        return router;
    }

    private void startTransports() {
        for (ServerConfig sc : appConfig.servers()) {
            if (sc.isStdio()) {
                Transport transport = transportFactory.create(sc);
                transport.start()
                    .onSuccess(v -> {
                        log.info("Stdio transport '{}' started", sc.name());
                        sseTransports.put(sc.name(), transport);
                    })
                    .onFailure(err -> log.error("Failed to start stdio transport '{}': {}", sc.name(), err.getMessage()));
            }
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        log.info("Shutting down MCP Gateway...");
        List<Future<Void>> stops = new java.util.ArrayList<>();
        sseTransports.values().forEach(t -> stops.add(t.stop()));
        Future.all(stops)
            .onComplete(ar -> {
                log.info("All transports stopped");
                stopPromise.complete();
            });
    }
}
