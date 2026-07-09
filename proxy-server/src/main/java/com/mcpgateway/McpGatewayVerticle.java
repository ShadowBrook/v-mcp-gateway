package com.mcpgateway;

import com.mcpgateway.config.AppConfig;
import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.handler.*;
import com.mcpgateway.service.StateManager;
import com.mcpgateway.session.impl.LocalSessionStore;
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
    private TransportFactory transportFactory;
    private SessionStore sessionStore;
    private StateManager stateManager;

    public McpGatewayVerticle(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        transportFactory = new TransportFactory(vertx);
        sessionStore = new LocalSessionStore();

        for (ServerConfig sc : appConfig.servers()) {
            serverConfigs.put(sc.name(), sc);
        }

        stateManager = new StateManager(serverConfigs, appConfig.tools(), transportFactory, sessionStore, vertx,
            appConfig.trustAll(), appConfig.blockInternal());

        Router router = setupRouter();

        int port = appConfig.port();
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(server -> {
                log.info("MCP Gateway listening on port {}", server.actualPort());
                startTransports();
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private Router setupRouter() {
        Router router = Router.router(vertx);

        // Build CORS handler from configured origins (default "*")
        List<String> origins = appConfig.corsOrigins();
        CorsHandler corsHandler = origins.contains("*")
            ? CorsHandler.create("*")
            : CorsHandler.create(origins.get(0));
        if (!origins.contains("*") && origins.size() > 1) {
            corsHandler.addOrigins(origins.subList(1, origins.size()));
        }
        router.route().handler(corsHandler
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.OPTIONS)
            .allowedMethod(HttpMethod.DELETE)
            .allowedHeader("*")
            .exposedHeader("Mcp-Session-Id")
            .exposedHeader("Content-Type"));

        router.route().handler(new LoggingHandler());

        router.get("/health_check").handler(HealthHandler.create());

        router.get("/:prefix/sse").handler(new SseHandler(stateManager));
        router.get("/sse").handler(new SseHandler(stateManager));

        router.post("/:prefix/message").handler(new MessageHandler(stateManager));

        router.get("/:prefix/mcp").handler(new McpStreamHandler(stateManager));
        router.post("/:prefix/mcp").handler(new McpHandler(stateManager));

        // Standalone local-tool endpoint with no backend MCP server required
        router.get("/mcp").handler(new LocalToolHandler(stateManager));
        router.post("/mcp").handler(new LocalToolHandler(stateManager));
        router.delete("/mcp").handler(new LocalToolHandler(stateManager));

        return router;
    }

    private void startTransports() {
        for (ServerConfig sc : appConfig.servers()) {
            if (sc.isStdio()) {
                // Use getOrCreateTransport so the transport is registered in StateManager's
                // internal cache — this ensures it will be stopped on gateway shutdown.
                Transport transport = stateManager.getOrCreateTransport(sc.name());
                if (transport != null) {
                    log.info("Stdio transport '{}' registered and starting", sc.name());
                } else {
                    log.error("Failed to create stdio transport '{}'", sc.name());
                }
            }
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        log.info("Shutting down MCP Gateway...");
        List<Future<Void>> stops = new java.util.ArrayList<>();
        stateManager.activeTransports().forEach(t -> stops.add(t.stop()));
        Future.all(stops)
            .onComplete(ar -> {
                log.info("All transports stopped");
                stopPromise.complete();
            });
    }
}
