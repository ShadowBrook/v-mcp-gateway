package com.mcpgateway.response;

import com.mcpgateway.domain.mcp.CallToolResult;
import com.mcpgateway.domain.mcp.ImageContent;
import io.vertx.core.buffer.Buffer;

import java.util.Base64;
import java.util.List;

public class ImageHandler implements ResponseHandler {

    @Override
    public boolean canHandle(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    @Override
    public CallToolResult handle(Buffer body, String contentType) {
        String base64 = Base64.getEncoder().encodeToString(body.getBytes());
        return CallToolResult.success(List.of(ImageContent.of(base64, contentType)));
    }

    @Override
    public ResponseHandler setNext(ResponseHandler next) {
        return next;
    }
}
