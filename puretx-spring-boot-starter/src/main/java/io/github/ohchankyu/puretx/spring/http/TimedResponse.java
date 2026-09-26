package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.Detection;
import io.github.ohchankyu.puretx.PuretxEngine;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A response whose detection finishes when the caller is done with it, not when it arrived.
 *
 * <p>The status and headers arrive first; the body streams afterwards, into whatever the caller
 * reads it with, and for a large response that is most of the call. A request factory that does
 * not buffer — Boot's default for {@code RestTemplateBuilder} and {@code RestClient.Builder} —
 * hands the body over as a stream, so a 500ms download inside a transaction read as 5ms and the
 * summary called it 1% of the transaction. The clock now stops when the response is closed, which
 * every Spring client does once it has extracted the body, or when the body has been read to its
 * end, whichever comes first.
 *
 * <p>A caller that takes the raw response and never closes it also never finishes the detection.
 * That caller is leaking a connection, which is the larger problem; puretx does not guess.
 */
final class TimedResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;

    private final PuretxEngine engine;

    private final Detection detection;

    private final AtomicBoolean finished = new AtomicBoolean();

    TimedResponse(final ClientHttpResponse delegate, final PuretxEngine engine, final Detection detection) {
        this.delegate = delegate;
        this.engine = engine;
        this.detection = detection;
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public InputStream getBody() throws IOException {
        return new FilterInputStream(delegate.getBody()) {
            @Override
            public int read() throws IOException {
                return finishAtEnd(super.read());
            }

            @Override
            public int read(final byte[] b, final int off, final int len) throws IOException {
                return finishAtEnd(super.read(b, off, len));
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    finish();
                }
            }

            private int finishAtEnd(final int read) {
                if (read < 0) {
                    finish();
                }
                return read;
            }
        };
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            finish();
        }
    }

    void finish() {
        if (finished.compareAndSet(false, true)) {
            engine.finish(detection);
        }
    }
}
