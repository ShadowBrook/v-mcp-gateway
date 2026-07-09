package com.mcpgateway.transport;

import com.mcpgateway.config.ServerConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StdioTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private final Vertx vertx;
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Process process;
    private BufferedReader stdoutReader;
    private BufferedWriter stdinWriter;

    private final ConcurrentHashMap<Object, Promise<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Object, Long> pendingTimers = new ConcurrentHashMap<>();

    public StdioTransport(Vertx vertx, ServerConfig config) {
        this.vertx = vertx;
        this.config = config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return "stdio";
    }

    @Override
    public Future<Void> start() {
        return vertx.executeBlocking(() -> {
            try {
                List<String> command = new java.util.ArrayList<>();
                command.add(config.command());
                if (config.args() != null) {
                    command.addAll(config.args());
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                if (config.env() != null && !config.env().isEmpty()) {
                    Map<String, String> env = pb.environment();
                    env.putAll(config.env());
                }

                process = pb.start();
                stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

                running.set(true);
                log.info("Stdio transport '{}' started: {}", config.name(), String.join(" ", command));

                // Start reading stdout in a separate daemon thread
                Thread stdoutThread = new Thread(this::readStdout, "stdio-" + config.name() + "-reader");
                stdoutThread.setDaemon(true);
                stdoutThread.start();

                // Start reading stderr in a separate daemon thread to prevent
                // subprocess deadlock when its stderr buffer fills up.
                Thread stderrThread = new Thread(this::readStderr, "stdio-" + config.name() + "-stderr");
                stderrThread.setDaemon(true);
                stderrThread.start();

                return null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to start stdio process for " + config.name(), e);
            }
        });
    }

    private void readStdout() {
        StringBuilder buffer = new StringBuilder();
        char[] cbuf = new char[4096];
        try {
            int read;
            while (running.get() && (read = stdoutReader.read(cbuf)) != -1) {
                buffer.append(cbuf, 0, read);

                // Process complete lines (JSON-RPC messages are line-delimited)
                int newlineIdx;
                while ((newlineIdx = buffer.indexOf("\n")) != -1) {
                    String line = buffer.substring(0, newlineIdx).trim();
                    buffer.delete(0, newlineIdx + 1);

                    if (!line.isEmpty()) {
                        processResponse(line);
                    }
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("Error reading stdout for '{}': {}", config.name(), e.getMessage());
            }
        } finally {
            // Process exited or stdout closed — clean up state
            running.set(false);
            cancelAllTimers();
            pendingRequests.forEach((id, promise) ->
                promise.fail("Stdio process exited unexpectedly"));
            pendingRequests.clear();
            log.warn("Stdio transport '{}' stdout closed", config.name());
        }
    }

    private void readStderr() {
        try (BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = stderrReader.readLine()) != null) {
                log.warn("[stdio-{}] {}", config.name(), line);
            }
        } catch (IOException e) {
            if (running.get()) {
                log.warn("Error reading stderr for '{}': {}", config.name(), e.getMessage());
            }
        }
    }

    private void processResponse(String line) {
        try {
            JsonObject response = new JsonObject(line);
            Object id = response.getValue("id");
            if (id != null) {
                Promise<JsonObject> promise = pendingRequests.remove(id);
                if (promise != null) {
                    removeTimer(id);
                    promise.complete(response);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse stdout line from '{}': {}", config.name(), line);
        }
    }

    @Override
    public Future<JsonObject> send(JsonObject request) {
        if (!running.get()) {
            return Future.failedFuture("Stdio transport not running");
        }

        Promise<JsonObject> promise = Promise.promise();
        Object id = request.getValue("id");

        vertx.executeBlocking(() -> {
            try {
                if (id != null) {
                    pendingRequests.put(id, promise);
                    long timerId = vertx.setTimer(config.timeout(), t -> {
                        Promise<JsonObject> p = pendingRequests.remove(id);
                        if (p != null) {
                            p.fail("Request timed out after " + config.timeout() + "ms");
                        }
                        pendingTimers.remove(id);
                    });
                    pendingTimers.put(id, timerId);
                }

                String json = request.encode() + "\n";
                stdinWriter.write(json);
                stdinWriter.flush();

                if (id == null) {
                    // Notification - complete immediately
                    promise.complete(new JsonObject());
                }
                return null;
            } catch (IOException e) {
                removePending(id);
                throw new RuntimeException("Failed to write to stdin: " + e.getMessage(), e);
            }
        }).onFailure(err -> {
            removePending(id);
            promise.fail(err);
        });

        return promise.future();
    }

    @Override
    public Future<Void> stop() {
        running.set(false);
        cancelAllTimers();
        pendingRequests.forEach((id, promise) ->
            promise.fail("Transport stopped"));
        pendingRequests.clear();

        return vertx.executeBlocking(() -> {
            try {
                if (stdinWriter != null) {
                    stdinWriter.close();
                }
                if (stdoutReader != null) {
                    stdoutReader.close();
                }
            } catch (IOException ignored) {
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Stdio transport '{}' stopped", config.name());
            return null;
        });
    }

    private void removePending(Object id) {
        pendingRequests.remove(id);
        removeTimer(id);
    }

    private void removeTimer(Object id) {
        Long timerId = pendingTimers.remove(id);
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
    }

    private void cancelAllTimers() {
        pendingTimers.values().forEach(vertx::cancelTimer);
        pendingTimers.clear();
    }

    @Override
    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }
}
