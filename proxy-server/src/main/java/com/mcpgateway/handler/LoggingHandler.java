package com.mcpgateway.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(LoggingHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        log.debug("{} {} from {}", ctx.request().method(), ctx.request().uri(), ctx.request().remoteAddress());
        ctx.next();
    }
}
