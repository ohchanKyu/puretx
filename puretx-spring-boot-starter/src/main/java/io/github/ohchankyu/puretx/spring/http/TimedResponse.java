package io.github.ohchankyu.puretx.spring.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * <p>A caller that takes the raw response and never closes it is leaking a connection, which is
 * the larger problem, but the call still happened inside the transaction and is still reported:
 * the detection is finished when the transaction ends, timed up to then.
 */
final class TimedResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;

    private final PendingDetection detection;

    TimedResponse(final ClientHttpResponse delegate, final PendingDetection detection) {
        this.delegate = delegate;
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
        detection.finish();
    }
}
