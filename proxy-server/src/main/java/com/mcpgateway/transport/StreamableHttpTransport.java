package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamableHttpTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpTransport.class);

    private final ServerConfig config;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(true);

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
                .putHeader("Accept", "application/json");

            httpClient.request(options)
                .compose(httpReq -> httpReq.send(Buffer.buffer(request.encode())))
                .compose(httpResp -> httpResp.body().map(body -> {
                    if (httpResp.statusCode() >= 400) {
                        throw new RuntimeException("Backend returned " + httpResp.statusCode() + ": " + body.toString());
                    }
                    String bodyStr = body.toString();
                    if (bodyStr.isBlank()) {
                        return new JsonObject();
                    }
                    return new JsonObject(bodyStr);
                }))
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        } catch (Exception e) {
            promise.fail(e);
        }

        return promise.future();
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
