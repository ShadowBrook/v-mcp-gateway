package com.mcpgateway;

import com.mcpgateway.config.ConfigLoader;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String configPath = "application.yml";
        if (args.length > 0) {
            configPath = args[0];
        }

        Vertx vertx = Vertx.vertx();
        ConfigLoader configLoader = new ConfigLoader(vertx);

        configLoader.load(configPath)
            .onSuccess(appConfig -> {
                log.info("Loaded config: port={}, {} servers configured", appConfig.port(), appConfig.servers().size());
                vertx.deployVerticle(new McpGatewayVerticle(appConfig))
                    .onSuccess(id -> log.info("MCP Gateway deployed with verticle ID: {}", id))
                    .onFailure(err -> {
                        log.error("Failed to deploy verticle", err);
                        vertx.close();
                    });
            })
            .onFailure(err -> {
                log.error("Failed to load config", err);
                vertx.close();
            });
    }
}
