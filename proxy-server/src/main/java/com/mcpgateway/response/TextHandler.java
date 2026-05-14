package com.mcpgateway.response;

import com.mcpgateway.domain.mcp.CallToolResult;
import com.mcpgateway.domain.mcp.TextContent;
import io.vertx.core.buffer.Buffer;

import java.util.List;

public class TextHandler implements ResponseHandler {

    @Override
    public boolean canHandle(String contentType) {
        return contentType != null && (contentType.startsWith("text/") ||
            contentType.contains("application/json") ||
            contentType.contains("application/xml"));
    }

    @Override
    public CallToolResult handle(Buffer body, String contentType) {
        return CallToolResult.success(List.of(TextContent.of(body.toString())));
    }

    @Override
    public ResponseHandler setNext(ResponseHandler next) {
        return next;
    }
}
