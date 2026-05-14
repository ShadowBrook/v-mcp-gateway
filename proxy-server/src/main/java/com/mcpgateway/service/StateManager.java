package com.mcpgateway.service;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.config.ToolConfig;
import com.mcpgateway.session.GatewaySession;
import com.mcpgateway.session.SessionStore;
import com.mcpgateway.transport.Transport;
import com.mcpgateway.transport.TransportFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);

    private final Map<String, ServerConfig> serverConfigs;
    private final Map<String, ToolConfig> toolConfigs = new ConcurrentHashMap<>();
    private final Map<String, Transport> transports = new ConcurrentHashMap<>();
    private final TransportFactory transportFactory;
    private final SessionStore sessionStore;
    private final ToolExecutor toolExecutor;

    public StateManager(Map<String, ServerConfig> serverConfigs,
                        List<ToolConfig> toolConfigs,
                        TransportFactory transportFactory,
                        SessionStore sessionStore,
                        Vertx vertx) {
        this.serverConfigs = serverConfigs;
        this.transportFactory = transportFactory;
        this.sessionStore = sessionStore;
        this.toolExecutor = new ToolExecutor(vertx, false);
        for (ToolConfig tc : toolConfigs) {
            this.toolConfigs.put(tc.name(), tc);
        }
    }

    // ---- Server config ----

    public ServerConfig getServerConfig(String prefix) {
        return serverConfigs.get(prefix);
    }

    public Map<String, ServerConfig> getServerConfigs() {
        return serverConfigs;
    }

    public int getServerCount() {
        return serverConfigs.size();
    }

    // ---- Tool config ----

    public ToolConfig getToolConfig(String name) {
        return toolConfigs.get(name);
    }

    public List<ToolConfig> getToolConfigs() {
        return List.copyOf(toolConfigs.values());
    }

    public int getToolCount() {
        return toolConfigs.size();
    }

    // ---- Transport ----

    public Transport getOrCreateTransport(String prefix) {
        ServerConfig sc = serverConfigs.get(prefix);
        if (sc == null) {
            return null;
        }
        return transports.computeIfAbsent(prefix, k -> {
            Transport t = transportFactory.create(sc);
            t.start().onFailure(err ->
                log.error("Failed to start transport for '{}': {}", k, err.getMessage()));
            return t;
        });
    }

    public Transport getTransport(String prefix) {
        return transports.get(prefix);
    }

    // ---- Session ----

    public Future<GatewaySession> createSession(String prefix, String sessionId,
                                                 Transport transport,
                                                 HttpServerResponse sseResponse) {
        GatewaySession session = new GatewaySession(sessionId, prefix, transport, sseResponse);
        return sessionStore.create(prefix, sessionId, session);
    }

    public Future<GatewaySession> getSession(String sessionId) {
        return sessionStore.get(sessionId);
    }

    public Future<Void> removeSession(String sessionId) {
        return sessionStore.remove(sessionId);
    }

    public Future<List<GatewaySession>> listSessionsByPrefix(String prefix) {
        return sessionStore.listByPrefix(prefix);
    }

    public int getSessionCount() {
        return sessionStore.count();
    }

    // ---- Tool executor ----

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public List<Transport> activeTransports() {
        return List.copyOf(transports.values());
    }
}
