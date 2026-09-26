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

    /** Headers and a first chunk go out at once; the rest of the body follows this much later. */
    private volatile long bodyDelayMillis;

    /** Status returned to every request. Set to 503 to make a retrying client actually retry. */
    private volatile int status = 200;

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
            final long bodyDelay = bodyDelayMillis;
            if (bodyDelay > 0) {
                exchange.sendResponseHeaders(status, 0);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write("ok".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    try {
                        Thread.sleep(bodyDelay);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    out.write("!".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            final byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
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

    public void setStatus(final int status) {
        this.status = status;
    }

    public void setDelayMillis(final long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public void setBodyDelayMillis(final long bodyDelayMillis) {
        this.bodyDelayMillis = bodyDelayMillis;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
