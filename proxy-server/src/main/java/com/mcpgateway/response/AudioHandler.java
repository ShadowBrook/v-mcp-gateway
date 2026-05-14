package com.mcpgateway.response;

import com.mcpgateway.domain.mcp.AudioContent;
import com.mcpgateway.domain.mcp.CallToolResult;
import io.vertx.core.buffer.Buffer;

import java.util.Base64;
import java.util.List;

public class AudioHandler implements ResponseHandler {

    @Override
    public boolean canHandle(String contentType) {
        return contentType != null && contentType.startsWith("audio/");
    }

    @Override
    public CallToolResult handle(Buffer body, String contentType) {
        String base64 = Base64.getEncoder().encodeToString(body.getBytes());
        return CallToolResult.success(List.of(AudioContent.of(base64, contentType)));
    }

    @Override
    public ResponseHandler setNext(ResponseHandler next) {
        return next;
    }
}
