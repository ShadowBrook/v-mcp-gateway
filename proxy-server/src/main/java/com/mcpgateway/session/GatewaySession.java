package com.mcpgateway.session;

import com.mcpgateway.transport.Transport;
import io.vertx.core.http.HttpServerResponse;

public class GatewaySession {

    private final String id;
    private final String prefix;
    private final Transport transport;
    private final HttpServerResponse sseResponse;

    public GatewaySession(String id, String prefix, Transport transport, HttpServerResponse sseResponse) {
        this.id = id;
        this.prefix = prefix;
        this.transport = transport;
        this.sseResponse = sseResponse;
    }

    public String id() {
        return id;
    }

    public String prefix() {
        return prefix;
    }

    public Transport transport() {
        return transport;
    }

    public HttpServerResponse sseResponse() {
        return sseResponse;
    }

    public void sendSse(String event, String data) {
        StringBuilder sb = new StringBuilder();
        if (event != null && !event.isBlank()) {
            sb.append("event: ").append(event).append("\n");
        }
        sb.append("data: ").append(data).append("\n\n");
        sseResponse.write(sb.toString());
    }
}
