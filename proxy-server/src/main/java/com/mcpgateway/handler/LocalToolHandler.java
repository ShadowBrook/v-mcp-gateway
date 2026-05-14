package com.mcpgateway.handler;

import com.mcpgateway.config.ToolConfig;
import com.mcpgateway.domain.mcp.*;
import com.mcpgateway.service.StateManager;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalToolHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(LocalToolHandler.class);

    private final StateManager stateManager;

    public LocalToolHandler(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void handle(RoutingContext ctx) {
        ctx.request().body().onSuccess(body -> {
            JsonObject request;
            try {
                String bodyStr = body.toString();
                request = bodyStr.isBlank() ? new JsonObject() : new JsonObject(bodyStr);
            } catch (Exception e) {
                respond(ctx, 400, new JsonObject().put("error", "Invalid JSON-RPC request"));
                return;
            }

            String method = request.getString("method", "");
            Object id = request.getValue("id");

            switch (method) {
                case "initialize" -> handleInitialize(ctx, id);
                case "notifications/initialized" -> respond(ctx, 202, null);
                case "tools/list" -> handleToolsList(ctx, id);
                case "tools/call" -> handleToolsCall(ctx, id, request.getJsonObject("params", new JsonObject()));
                case "ping" -> {
                    if (id != null) {
                        var resp = JsonRpcResponse.success(id, new JsonObject());
                        respond(ctx, 200, resp.toJson());
                    } else {
                        respond(ctx, 202, null);
                    }
                }
                default -> {
                    var resp = JsonRpcResponse.failure(id,
                        JsonRpcError.of(-32601, "Method not found: " + method));
                    respond(ctx, 200, resp.toJson());
                }
            }
        }).onFailure(err ->
            respond(ctx, 400, new JsonObject().put("error", "Failed to read request body")));
    }

    private void handleInitialize(RoutingContext ctx, Object id) {
        JsonObject result = new JsonObject()
            .put("protocolVersion", "2025-11-25")
            .put("capabilities", new JsonObject()
                .put("tools", new JsonObject().put("listChanged", false)))
            .put("serverInfo", new JsonObject()
                .put("name", "mcp-gateway-local")
                .put("version", "1.0.0"));
        var resp = JsonRpcResponse.success(id, result);
        respond(ctx, 200, resp.toJson());
    }

    private void handleToolsList(RoutingContext ctx, Object id) {
        JsonArray tools = new JsonArray();
        for (ToolConfig tc : stateManager.getToolConfigs()) {
            JsonObject tool = new JsonObject()
                .put("name", tc.name())
                .put("description", tc.description())
                .put("inputSchema", tc.inputSchema() != null ? tc.inputSchema() : new JsonObject().put("type", "object"));
            tools.add(tool);
        }
        JsonObject result = new JsonObject().put("tools", tools);
        var resp = JsonRpcResponse.success(id, result);
        respond(ctx, 200, resp.toJson());
    }

    private void handleToolsCall(RoutingContext ctx, Object id, JsonObject params) {
        String toolName = params.getString("name", "");
        ToolConfig toolConfig = stateManager.getToolConfig(toolName);
        if (toolConfig == null) {
            var resp = JsonRpcResponse.failure(id,
                JsonRpcError.of(-32602, "Unknown tool: " + toolName));
            respond(ctx, 200, resp.toJson());
            return;
        }

        JsonObject arguments = params.getJsonObject("arguments", new JsonObject());
        CallToolParams callParams = new CallToolParams(toolConfig.name(), arguments);

        stateManager.getToolExecutor().execute(toolConfig, callParams)
            .onSuccess(result -> {
                JsonArray content = toJsonContent(result.content());
                JsonObject resultObj = new JsonObject()
                    .put("content", content)
                    .put("isError", result.isError());
                var resp = JsonRpcResponse.success(id, resultObj);
                respond(ctx, 200, resp.toJson());
            })
            .onFailure(err -> {
                log.error("Local tool execution failed: {}", err.getMessage());
                var resp = JsonRpcResponse.failure(id,
                    JsonRpcError.of(-32603, "Tool execution failed: " + err.getMessage()));
                respond(ctx, 500, resp.toJson());
            });
    }

    private void respond(RoutingContext ctx, int status, JsonObject body) {
        var resp = ctx.response();
        resp.setStatusCode(status)
            .putHeader("Content-Type", "application/json");
        if (body != null) {
            resp.end(body.encode());
        } else {
            resp.end();
        }
    }

    private JsonArray toJsonContent(java.util.List<Content> content) {
        JsonArray arr = new JsonArray();
        for (Content c : content) {
            JsonObject item = new JsonObject().put("type", c.type());
            switch (c) {
                case TextContent tc -> item.put("text", tc.text());
                case ImageContent ic -> {
                    item.put("data", ic.data());
                    item.put("mimeType", ic.mimeType());
                }
                case AudioContent ac -> {
                    item.put("data", ac.data());
                    item.put("mimeType", ac.mimeType());
                }
            }
            arr.add(item);
        }
        return arr;
    }
}
