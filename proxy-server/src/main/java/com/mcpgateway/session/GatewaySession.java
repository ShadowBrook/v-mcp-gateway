package com.mcpgateway.session;

import com.mcpgateway.transport.Transport;
import io.vertx.core.http.HttpServerResponse;

public record GatewaySession(String id, String prefix, Transport transport, HttpServerResponse sseResponse) {

    public void sendSse(String event, String data) {
        if (sseResponse.closed() || sseResponse.ended()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (event != null && !event.isBlank()) {
            sb.append("event: ").append(event).append("\n");
        }
        sb.append("data: ").append(data).append("\n\n");
        try {
            sseResponse.write(sb.toString());
        } catch (Exception e) {
            // Response may have been closed between check and write
        }
    }
}
