package com.mcpgateway.handler;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.config.ToolConfig;
import com.mcpgateway.domain.mcp.*;
import com.mcpgateway.service.StateManager;
import com.mcpgateway.transport.Transport;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(McpHandler.class);

    private final StateManager stateManager;

    public McpHandler(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String prefix = ctx.pathParam("prefix");
        ServerConfig serverConfig = stateManager.getServerConfig(prefix);
        if (serverConfig == null) {
            respond(ctx, 404,
                new JsonObject().put("error", "No MCP server configured for prefix: " + prefix));
            return;
        }

        if ("sse".equals(serverConfig.type())) {
            respond(ctx, 400,
                new JsonObject().put("error", "SSE endpoint discovery required — connect via GET /" + prefix + "/sse"));
            return;
        }

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

            Transport transport = stateManager.getOrCreateTransport(prefix);
            if (transport == null) {
                respond(ctx, 500, new JsonObject().put("error", "Failed to create transport"));
                return;
            }

            switch (method) {
                case "tools/list" -> handleToolsList(ctx, id, transport);
                case "tools/call" -> handleToolsCall(ctx, id, transport, request);
                case "prompts/list" -> handlePromptsList(ctx, id, transport);
                case "prompts/get" -> handlePromptsGet(ctx, id, transport, request.getJsonObject("params", new JsonObject()));
                default -> transport.sendStreaming(request, ctx.response());
            }
        }).onFailure(err ->
            respond(ctx, 400, new JsonObject().put("error", "Failed to read request body")));
    }

    // ---- tools/list ----
    private void handleToolsList(RoutingContext ctx, Object id, Transport transport) {
        transport.fetchTools()
            .onSuccess(backendTools -> {
                JsonArray tools = new JsonArray();
                // Backend tools first
                for (var t : backendTools) {
                    tools.add(t.toJson());
                }
                // Merge local tools
                for (ToolConfig tc : stateManager.getToolConfigs()) {
                    JsonObject tool = new JsonObject()
                        .put("name", tc.name())
                        .put("description", tc.description())
                        .put("inputSchema", tc.inputSchema() != null ? tc.inputSchema() : new JsonObject().put("type", "object"));
                    tools.add(tool);
                }
                JsonObject result = new JsonObject().put("tools", tools);
                respond(ctx, 200, JsonRpcResponse.success(id, result).toJson());
            })
            .onFailure(err -> {
                log.error("Failed to fetch backend tools: {}", err.getMessage());
                respond(ctx, 200, JsonRpcResponse.failure(id,
                    JsonRpcError.of(-32603, "Failed to fetch backend tools: " + err.getMessage())).toJson());
            });
    }

    // ---- prompts/list ----
    private void handlePromptsList(RoutingContext ctx, Object id, Transport transport) {
        transport.fetchPrompts()
            .onSuccess(prompts -> {
                JsonArray arr = new JsonArray();
                for (PromptSchema p : prompts) {
                    arr.add(p.toJson());
                }
                JsonObject result = new JsonObject().put("prompts", arr);
                respond(ctx, 200, JsonRpcResponse.success(id, result).toJson());
            })
            .onFailure(err -> {
                log.error("Failed to fetch prompts: {}", err.getMessage());
                respond(ctx, 200, JsonRpcResponse.failure(id,
                    JsonRpcError.of(-32603, "Failed to fetch prompts: " + err.getMessage())).toJson());
            });
    }

    // ---- prompts/get ----
    private void handlePromptsGet(RoutingContext ctx, Object id, Transport transport, JsonObject params) {
        String promptName = params.getString("name", "");
        if (promptName.isBlank()) {
            respond(ctx, 200, JsonRpcResponse.failure(id,
                JsonRpcError.of(-32602, "Missing prompt name")).toJson());
            return;
        }
        transport.fetchPrompt(promptName)
            .onSuccess(prompt -> {
                if (prompt != null) {
                    respond(ctx, 200, JsonRpcResponse.success(id, prompt.toJson()).toJson());
                } else {
                    respond(ctx, 200, JsonRpcResponse.failure(id,
                        JsonRpcError.of(-32602, "Prompt not found: " + promptName)).toJson());
                }
            })
            .onFailure(err -> {
                log.error("Failed to fetch prompt '{}': {}", promptName, err.getMessage());
                respond(ctx, 200, JsonRpcResponse.failure(id,
                    JsonRpcError.of(-32603, "Failed to fetch prompt: " + err.getMessage())).toJson());
            });
    }

    // ---- tools/call ----
    private void handleToolsCall(RoutingContext ctx, Object id, Transport transport, JsonObject request) {
        JsonObject params = request.getJsonObject("params", new JsonObject());
        String toolName = params.getString("name", "");
        ToolConfig toolConfig = stateManager.getToolConfig(toolName);
        if (toolConfig != null) {
            JsonObject arguments = params.getJsonObject("arguments", new JsonObject());
            CallToolParams callParams = new CallToolParams(toolConfig.name(), arguments);
            stateManager.getToolExecutor().execute(toolConfig, callParams)
                .onSuccess(result -> {
                    JsonArray content = toJsonContent(result.content());
                    JsonObject resultObj = new JsonObject()
                        .put("content", content)
                        .put("isError", result.isError());
                    respond(ctx, 200, JsonRpcResponse.success(id, resultObj).toJson());
                })
                .onFailure(err -> {
                    log.error("Local tool execution failed: {}", err.getMessage());
                    respond(ctx, 500, JsonRpcResponse.failure(id,
                        JsonRpcError.of(-32603, "Tool execution failed: " + err.getMessage())).toJson());
                });
        } else {
            transport.sendStreaming(request, ctx.response());
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

    private void respond(RoutingContext ctx, int status, JsonObject body) {
        ctx.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(body.encode());
    }
}
