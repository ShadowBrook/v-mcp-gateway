package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SseTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private final ServerConfig config;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ConcurrentHashMap<Object, Promise<JsonObject>> pendingRequests = new ConcurrentHashMap<>();

    private String backendMessageUrl;
    private String sseUrl;

    public SseTransport(Vertx vertx, ServerConfig config) {
        this.config = config;
        this.httpClient = vertx.createHttpClient();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return "sse";
    }

    @Override
    public Future<Void> start() {
        Promise<Void> promise = Promise.promise();
        sseUrl = config.url();

        if (sseUrl == null || sseUrl.isBlank()) {
            return Future.failedFuture("SSE URL is required for SSE transport");
        }

        connectSse(sseUrl, promise);
        return promise.future();
    }

    private void connectSse(String url, Promise<Void> startPromise) {
        try {
            URI uri = URI.create(url);
            int port = uri.getPort() >= 0 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }

            RequestOptions options = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setHost(host)
                .setPort(port)
                .setURI(path)
                .setSsl(uri.getScheme().equals("https"))
                .setFollowRedirects(true);

            httpClient.request(options)
                .compose(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        startPromise.fail("SSE connection failed with status " + response.statusCode());
                        return;
                    }
                    running.set(true);
                    startPromise.complete();
                    handleSseResponse(response);
                })
                .onFailure(err -> {
                    startPromise.fail(err);
                    log.error("Failed to connect to SSE backend: {}", url, err);
                });
        } catch (Exception e) {
            startPromise.fail(e);
        }
    }

    private void handleSseResponse(HttpClientResponse response) {
        StringBuilder lineBuffer = new StringBuilder();
        AtomicReference<String> currentEvent = new AtomicReference<>(null);

        response.handler(chunk -> {
            String data = chunk.toString();
            lineBuffer.append(data);

            // Process complete lines
            while (true) {
                int newlineIdx = lineBuffer.indexOf("\n");
                if (newlineIdx < 0) break;

                String line = lineBuffer.substring(0, newlineIdx).replace("\r", "");
                lineBuffer.delete(0, newlineIdx + 1);

                if (line.isEmpty()) {
                    // Empty line = dispatch event boundary
                    currentEvent.set(null);
                    continue;
                }

                if (line.startsWith("event:")) {
                    currentEvent.set(line.substring(6).trim());
                } else if (line.startsWith("data:")) {
                    String eventData = line.substring(5).trim();
                    String eventType = currentEvent.get();
                    if ("endpoint".equals(eventType)) {
                        backendMessageUrl = eventData;
                        log.info("SSE transport '{}' received endpoint: {}", config.name(), backendMessageUrl);
                    } else if ("message".equals(eventType)) {
                        handleMessageEvent(eventData);
                    }
                }
            }
        });

        response.endHandler(v -> {
            if (running.get()) {
                log.warn("SSE connection for '{}' ended unexpectedly, attempting reconnect", config.name());
                running.set(false);
                // Fail all pending requests
                pendingRequests.forEach((id, promise) ->
                    promise.fail("SSE connection closed"));
                pendingRequests.clear();
            }
        });

        response.exceptionHandler(err -> {
            log.error("SSE connection error for '{}': {}", config.name(), err.getMessage());
            if (running.get()) {
                running.set(false);
                pendingRequests.forEach((id, promise) ->
                    promise.fail("SSE connection error: " + err.getMessage()));
                pendingRequests.clear();
            }
        });
    }

    private void handleMessageEvent(String data) {
        try {
            JsonObject response = new JsonObject(data);
            Object id = response.getValue("id");
            if (id != null) {
                Promise<JsonObject> promise = pendingRequests.remove(id);
                if (promise != null) {
                    promise.complete(response);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SSE message data: {}", data, e);
        }
    }

    @Override
    public Future<JsonObject> send(JsonObject request) {
        if (!running.get()) {
            return Future.failedFuture("SSE transport not running");
        }
        if (backendMessageUrl == null) {
            return Future.failedFuture("No backend message URL - wait for endpoint event");
        }

        Promise<JsonObject> promise = Promise.promise();
        Object id = request.getValue("id");
        if (id != null) {
            pendingRequests.put(id, promise);
        }

        try {
            // Resolve the message URL relative to SSE URL
            String resolvedUrl = resolveUrl(backendMessageUrl);

            URI uri = URI.create(resolvedUrl);
            int port = uri.getPort() >= 0 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }

            RequestOptions options = new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setHost(host)
                .setPort(port)
                .setURI(path)
                .setSsl(uri.getScheme().equals("https"))
                .setFollowRedirects(true)
                .putHeader("Content-Type", "application/json");

            httpClient.request(options)
                .compose(httpReq -> httpReq.send(Buffer.buffer(request.encode())))
                .onSuccess(httpResp -> {
                    if (httpResp.statusCode() >= 400) {
                        httpResp.body().onSuccess(body -> {
                            pendingRequests.remove(id);
                            promise.fail("Backend returned " + httpResp.statusCode() + ": " + body.toString());
                        });
                        return;
                    }
                    // For notifications (no id), complete immediately
                    if (id == null) {
                        // Response comes via SSE stream, but notifications have no response
                        promise.complete(new JsonObject());
                    }
                    // For requests with id, the response comes via SSE
                    // The promise is already registered in pendingRequests
                })
                .onFailure(err -> {
                    pendingRequests.remove(id);
                    promise.fail(err);
                });
        } catch (Exception e) {
            pendingRequests.remove(id);
            promise.fail(e);
        }

        return promise.future();
    }

    private String resolveUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        try {
            URI sseUri = URI.create(sseUrl);
            URI resolved = sseUri.resolve(url);
            return resolved.toString();
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public Future<Void> stop() {
        running.set(false);
        pendingRequests.forEach((id, promise) ->
            promise.fail("Transport stopped"));
        pendingRequests.clear();
        return httpClient.close();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
