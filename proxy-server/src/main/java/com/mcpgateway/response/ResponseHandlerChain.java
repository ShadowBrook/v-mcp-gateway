package com.mcpgateway.response;

import com.mcpgateway.domain.mcp.CallToolResult;
import com.mcpgateway.domain.mcp.TextContent;
import io.vertx.core.buffer.Buffer;

import java.util.List;

public class ResponseHandlerChain {

    private final List<ResponseHandler> handlers;

    public ResponseHandlerChain() {
        this.handlers = List.of(
            new TextHandler(),
            new ImageHandler(),
            new AudioHandler()
        );
    }

    public CallToolResult process(Buffer body, String contentType) {
        for (ResponseHandler handler : handlers) {
            if (handler.canHandle(contentType)) {
                return handler.handle(body, contentType);
            }
        }
        // Fallback: treat as text
        return CallToolResult.success(List.of(TextContent.of(body.toString())));
    }
}
