package com.acme.orders;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** A real HTTP endpoint on localhost, so the tests exercise the actual client stack. */
public final class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private volatile long delayMillis;

    public StubHttpServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("could not start stub server", ex);
        }
        server.createContext("/", exchange -> {
            long delay = delayMillis;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String url() {
        return url("/charge");
    }

    public String url(final String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public void setDelayMillis(final long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
