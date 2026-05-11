package com.mcpgateway.handler;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class HealthHandler implements Handler<RoutingContext> {

    public static HealthHandler create() {
        return new HealthHandler();
    }

    @Override
    public void handle(RoutingContext ctx) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("status", "ok").encode());
    }
}
