package com.mcpgateway.response;

import com.mcpgateway.domain.mcp.CallToolResult;
import io.vertx.core.buffer.Buffer;

public interface ResponseHandler {

    boolean canHandle(String contentType);

    CallToolResult handle(Buffer body, String contentType);

    ResponseHandler setNext(ResponseHandler next);
}
