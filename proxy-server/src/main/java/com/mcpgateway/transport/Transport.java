package com.mcpgateway.transport;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Transport {

    String name();

    String type();

    Future<Void> start();

    Future<Void> stop();

    Future<JsonObject> send(JsonObject request);

    boolean isRunning();
}
