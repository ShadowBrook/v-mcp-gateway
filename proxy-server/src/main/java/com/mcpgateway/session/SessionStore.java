package com.mcpgateway.session;

import io.vertx.core.Future;
import java.util.List;

public interface SessionStore {

    Future<GatewaySession> create(String prefix, String sessionId, GatewaySession session);

    Future<GatewaySession> get(String sessionId);

    Future<Void> remove(String sessionId);

    Future<List<GatewaySession>> listByPrefix(String prefix);

    int count();
}
