package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import com.mcpgateway.domain.mcp.JsonRpcError;
import com.mcpgateway.domain.mcp.JsonRpcResponse;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class StreamableHttpTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpTransport.class);

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final ServerConfig config;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<String> backendSessionId = new AtomicReference<>();

    public StreamableHttpTransport(Vertx vertx, ServerConfig config) {
        this.config = config;
        this.httpClient = vertx.createHttpClient();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return "streamable-http";
    }

    @Override
    public String getSessionId() {
        return backendSessionId.get();
    }

    @Override
    public Future<Void> start() {
        running.set(true);
        return Future.succeededFuture();
    }

    @Override
    public Future<JsonObject> send(JsonObject request) {
        String url = config.url();
        if (url == null || url.isBlank()) {
            return Future.failedFuture("URL is required for streamable-http transport");
        }

        Promise<JsonObject> promise = Promise.promise();
        try {
            URI uri = URI.create(url);
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
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json, text/event-stream");

            // Forward backend session ID if we have one
            String sid = backendSessionId.get();
            if (sid != null && !sid.isBlank()) {
                options.putHeader(SESSION_HEADER, sid);
            }

            httpClient.request(options)
                .compose(httpReq -> httpReq.send(Buffer.buffer(request.encode())))
                .compose(httpResp -> {
                    // Capture session ID from backend response
                    String newSid = httpResp.getHeader(SESSION_HEADER);
                    if (newSid != null && !newSid.isBlank()) {
                        String oldSid = backendSessionId.getAndSet(newSid);
                        if (!newSid.equals(oldSid)) {
                            log.info("Streamable HTTP transport '{}' session ID: {}", config.name(), newSid);
                        }
                    }

                    if (httpResp.statusCode() >= 400) {
                        return httpResp.body().map(body ->
                            { throw new RuntimeException("Backend returned " + httpResp.statusCode() + ": " + body.toString()); }
                        );
                    }

                    String contentType = httpResp.getHeader("Content-Type");
                    if (contentType != null && contentType.contains("text/event-stream")) {
                        return handleSseResponse(httpResp);
                    }
                    return handleJsonResponse(httpResp);
                })
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        } catch (Exception e) {
            promise.fail(e);
        }

        return promise.future();
    }

    @Override
    public Future<Void> sendStreaming(JsonObject request, HttpServerResponse clientResponse) {
        String url = config.url();
        if (url == null || url.isBlank()) {
            String err = JsonRpcResponse.failure(request.getValue("id"),
                JsonRpcError.of(-32603, "URL is required")).toJson().encode();
            clientResponse.setStatusCode(502).putHeader("Content-Type", "application/json").end(err);
            return Future.succeededFuture();
        }

        Promise<Void> promise = Promise.promise();
        try {
            URI uri = URI.create(url);
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
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json, text/event-stream");

            String sid = backendSessionId.get();
            if (sid != null && !sid.isBlank()) {
                options.putHeader(SESSION_HEADER, sid);
            }

            httpClient.request(options)
                .compose(httpReq -> httpReq.send(Buffer.buffer(request.encode())))
                .onSuccess(httpResp -> {
                    // Capture session ID from backend response
                    String newSid = httpResp.getHeader(SESSION_HEADER);
                    if (newSid != null && !newSid.isBlank()) {
                        String oldSid = backendSessionId.getAndSet(newSid);
                        if (!newSid.equals(oldSid)) {
                            log.info("Streamable HTTP transport '{}' session ID: {}", config.name(), newSid);
                        }
                    }

                    if (httpResp.statusCode() >= 400) {
                        httpResp.body().onSuccess(body -> {
                            String err = JsonRpcResponse.failure(request.getValue("id"),
                                JsonRpcError.of(-32603, "Backend returned " + httpResp.statusCode() + ": " + body))
                                .toJson().encode();
                            clientResponse.setStatusCode(502)
                                .putHeader("Content-Type", "application/json")
                                .end(err);
                            promise.complete();
                        });
                        return;
                    }

                    String contentType = httpResp.getHeader("Content-Type");
                    if (contentType != null && contentType.contains("text/event-stream")) {
                        // Forward session ID to client
                        String backendSid = backendSessionId.get();
                        if (backendSid != null && !backendSid.isBlank()) {
                            clientResponse.putHeader(SESSION_HEADER, backendSid);
                        }
                        clientResponse.setStatusCode(200)
                            .putHeader("Content-Type", "text/event-stream")
                            .putHeader("Cache-Control", "no-cache")
                            .putHeader("Connection", "keep-alive")
                            .setChunked(true);

                        httpResp.handler(chunk -> clientResponse.write(chunk.toString()));
                        httpResp.endHandler(v -> {
                            clientResponse.end();
                            promise.complete();
                        });
                        httpResp.exceptionHandler(err -> {
                            log.error("SSE stream error for '{}': {}", config.name(), err.getMessage());
                            if (!clientResponse.ended()) {
                                clientResponse.end();
                            }
                            promise.fail(err);
                        });
                    } else {
                        // JSON response (or empty body for 202)
                        int statusCode = httpResp.statusCode();
                        httpResp.body().onSuccess(body -> {
                            String bodyStr = body.toString();
                            String backendSid = backendSessionId.get();
                            if (backendSid != null && !backendSid.isBlank()) {
                                clientResponse.putHeader(SESSION_HEADER, backendSid);
                            }
                            clientResponse.setStatusCode(statusCode)
                                .putHeader("Content-Type", "application/json");
                            if (bodyStr.isBlank()) {
                                clientResponse.end();
                            } else {
                                clientResponse.end(bodyStr);
                            }
                            promise.complete();
                        }).onFailure(err -> {
                            log.error("Failed to read backend response for '{}': {}", config.name(), err.getMessage());
                            String errorBody = JsonRpcResponse.failure(request.getValue("id"),
                                JsonRpcError.of(-32603, "Failed to read backend response"))
                                .toJson().encode();
                            clientResponse.setStatusCode(502)
                                .putHeader("Content-Type", "application/json")
                                .end(errorBody);
                            promise.fail(err);
                        });
                    }
                })
                .onFailure(err -> {
                    log.error("Transport error for '{}': {}", config.name(), err.getMessage());
                    String errorBody = JsonRpcResponse.failure(request.getValue("id"),
                        JsonRpcError.of(-32603, "Transport error: " + err.getMessage()))
                        .toJson().encode();
                    clientResponse.setStatusCode(502)
                        .putHeader("Content-Type", "application/json")
                        .end(errorBody);
                    promise.fail(err);
                });
        } catch (Exception e) {
            String errorBody = JsonRpcResponse.failure(request.getValue("id"),
                JsonRpcError.of(-32603, "Invalid URL: " + e.getMessage()))
                .toJson().encode();
            clientResponse.setStatusCode(502).putHeader("Content-Type", "application/json").end(errorBody);
            promise.fail(e);
        }

        return promise.future();
    }

    private Future<JsonObject> handleJsonResponse(HttpClientResponse httpResp) {
        return httpResp.body().map(body -> {
            String bodyStr = body.toString();
            if (bodyStr.isBlank()) {
                return new JsonObject();
            }
            return new JsonObject(bodyStr);
        });
    }

    private Future<JsonObject> handleSseResponse(HttpClientResponse httpResp) {
        Promise<JsonObject> promise = Promise.promise();
        StringBuilder lineBuffer = new StringBuilder();
        AtomicReference<String> currentEvent = new AtomicReference<>(null);

        httpResp.handler(chunk -> {
            lineBuffer.append(chunk.toString());
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
                    if ("message".equals(eventType) || eventType == null || eventType.isBlank()) {
                        try {
                            JsonObject response = new JsonObject(eventData);
                            // Only complete on the first valid JSON-RPC message
                            if (!promise.future().isComplete()) {
                                promise.complete(response);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE data as JSON: {}", eventData, e);
                        }
                    }
                }
            }
        });

        httpResp.endHandler(v -> {
            if (!promise.future().isComplete()) {
                promise.fail("SSE stream ended without a valid response");
            }
        });

        httpResp.exceptionHandler(err -> {
            if (!promise.future().isComplete()) {
                promise.fail(err);
            }
        });

        return promise.future();
    }

    @Override
    public Future<Void> connectStream(HttpServerResponse clientResponse) {
        log.info("connectStream called for '{}', session: {}", config.name(), backendSessionId.get());
        String url = config.url();
        if (url == null || url.isBlank()) {
            clientResponse.setStatusCode(502).end("Backend URL not configured");
            return Future.succeededFuture();
        }

        Promise<Void> promise = Promise.promise();

        // Send headers and keepalive immediately — don't wait for backend
        clientResponse.setStatusCode(200)
            .putHeader("Content-Type", "text/event-stream")
            .putHeader("Cache-Control", "no-cache")
            .putHeader("Connection", "keep-alive")
            .setChunked(true)
            .write(Buffer.buffer(":ok\n\n"));

        // Handle client disconnect
        clientResponse.closeHandler(v -> {
            log.info("Client disconnected SSE stream for '{}'", config.name());
            doCleanup(clientResponse, promise);
        });

        // Now connect to backend asynchronously and pipe its stream
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
                .setFollowRedirects(true)
                .putHeader("Accept", "text/event-stream");

            String sid = backendSessionId.get();
            if (sid != null && !sid.isBlank()) {
                options.putHeader(SESSION_HEADER, sid);
            }

            httpClient.request(options)
                .compose(HttpClientRequest::send)
                .onSuccess(httpResp -> {
                    if (httpResp.statusCode() != 200) {
                        log.warn("Backend GET SSE returned {} for '{}'", httpResp.statusCode(), config.name());
                        doCleanup(clientResponse, promise);
                        return;
                    }

                    String newSid = httpResp.getHeader(SESSION_HEADER);
                    if (newSid != null && !newSid.isBlank()) {
                        backendSessionId.set(newSid);
                    }

                    httpResp.handler(chunk -> {
                        if (!clientResponse.ended()) {
                            clientResponse.write(chunk);
                        }
                    });

                    httpResp.endHandler(v -> doCleanup(clientResponse, promise));
                    httpResp.exceptionHandler(err -> {
                        log.error("Backend SSE stream error for '{}': {}", config.name(), err.getMessage());
                        doCleanup(clientResponse, promise);
                    });
                })
                .onFailure(err -> {
                    log.error("Failed to connect SSE stream for '{}': {}", config.name(), err.getMessage());
                    doCleanup(clientResponse, promise);
                });
        } catch (Exception e) {
            log.error("Invalid URL for '{}': {}", config.name(), e.getMessage());
            doCleanup(clientResponse, promise);
        }

        return promise.future();
    }


    private void doCleanup(HttpServerResponse clientResponse, Promise<Void> promise) {
        if (!clientResponse.ended()) {
            clientResponse.end();
        }
        promise.tryComplete();
    }

    @Override
    public Future<Void> stop() {
        running.set(false);
        return httpClient.close();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
