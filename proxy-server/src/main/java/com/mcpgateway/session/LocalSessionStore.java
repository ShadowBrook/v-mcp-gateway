package com.mcpgateway.session;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LocalSessionStore implements SessionStore {

    private final ConcurrentHashMap<String, GatewaySession> sessions = new ConcurrentHashMap<>();

    @Override
    public Future<GatewaySession> create(String prefix, String sessionId, GatewaySession session) {
        sessions.put(sessionId, session);
        return Future.succeededFuture(session);
    }

    @Override
    public Future<GatewaySession> get(String sessionId) {
        GatewaySession session = sessions.get(sessionId);
        if (session != null) {
            return Future.succeededFuture(session);
        }
        return Future.failedFuture("Session not found: " + sessionId);
    }

    @Override
    public Future<Void> remove(String sessionId) {
        sessions.remove(sessionId);
        return Future.succeededFuture();
    }

    @Override
    public Future<List<GatewaySession>> listByPrefix(String prefix) {
        List<GatewaySession> result = sessions.values().stream()
            .filter(s -> s.prefix().equals(prefix))
            .collect(Collectors.toList());
        return Future.succeededFuture(result);
    }

    @Override
    public int count() {
        return sessions.size();
    }
}
