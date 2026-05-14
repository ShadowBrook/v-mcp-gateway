package com.mcpgateway.service;

import com.mcpgateway.config.ToolConfig;
import com.mcpgateway.domain.mcp.CallToolParams;
import com.mcpgateway.domain.mcp.CallToolResult;
import com.mcpgateway.domain.mcp.TextContent;
import com.mcpgateway.response.ResponseHandlerChain;
import com.mcpgateway.security.InternalNetworkValidator;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final WebClient webClient;
    private final ResponseHandlerChain responseChain;
    private final InternalNetworkValidator networkValidator;
    private final boolean blockInternal;

    public ToolExecutor(Vertx vertx, boolean blockInternal) {
        this.webClient = WebClient.create(vertx,
            new WebClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setFollowRedirects(true));
        this.responseChain = new ResponseHandlerChain();
        this.networkValidator = new InternalNetworkValidator();
        this.blockInternal = blockInternal;
    }

    public Future<CallToolResult> execute(ToolConfig tool, CallToolParams params) {
        Promise<CallToolResult> promise = Promise.promise();

        try {
            String resolvedUrl = resolveUrl(tool.url(), params.arguments());

            if (blockInternal && networkValidator.isInternal(resolvedUrl)) {
                log.warn("Blocked request to internal address: {}", resolvedUrl);
                promise.complete(CallToolResult.error(
                    List.of(TextContent.of("Blocked: target URL resolves to internal network"))));
                return promise.future();
            }

            var request = switch (tool.method().toUpperCase()) {
                case "POST" -> webClient.postAbs(resolvedUrl);
                case "PUT" -> webClient.putAbs(resolvedUrl);
                case "DELETE" -> webClient.deleteAbs(resolvedUrl);
                default -> webClient.getAbs(resolvedUrl);
            };

            // Apply configured headers
            if (tool.headers() != null) {
                tool.headers().forEach(request::putHeader);
            }

            // Build body from template or arguments
            Buffer body = buildBody(tool, params);

            request.sendBuffer(body)
                .onSuccess(response -> {
                    String contentType = response.getHeader("Content-Type");
                    Buffer responseBody = response.bodyAsBuffer();
                    if (response.statusCode() >= 400) {
                        promise.complete(CallToolResult.error(
                            List.of(TextContent.of("HTTP " + response.statusCode() + ": " + responseBody))));
                        return;
                    }
                    promise.complete(responseChain.process(responseBody, contentType));
                })
                .onFailure(err -> promise.complete(CallToolResult.error(
                    List.of(TextContent.of("Tool execution failed: " + err.getMessage())))));
        } catch (Exception e) {
            promise.complete(CallToolResult.error(
                List.of(TextContent.of("Invalid tool configuration: " + e.getMessage()))));
        }

        return promise.future();
    }

    private String resolveUrl(String template, io.vertx.core.json.JsonObject args) {
        String result = template;
        for (Map.Entry<String, Object> entry : args) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Buffer buildBody(ToolConfig tool, CallToolParams params) {
        if (tool.bodyTemplate() != null && !tool.bodyTemplate().isBlank()) {
            String body = resolveUrl(tool.bodyTemplate(), params.arguments());
            return Buffer.buffer(body);
        }
        return Buffer.buffer(params.arguments().encode());
    }
}
