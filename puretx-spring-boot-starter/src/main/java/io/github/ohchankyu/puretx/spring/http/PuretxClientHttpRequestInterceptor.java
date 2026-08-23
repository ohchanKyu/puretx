package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.Detection;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.Ordered;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.client.RestTemplate;

/**
 * Catches {@code RestTemplate} and {@code RestClient} calls made inside a transaction.
 *
 * <p>Runs at the outermost position in the interceptor chain so the reported duration is the whole
 * call — retries, redirects, authentication round-trips and all. That number is the point: it is
 * how long a database connection was held hostage by a remote server.
 */
public final class PuretxClientHttpRequestInterceptor implements ClientHttpRequestInterceptor, Ordered {

    private final Supplier<PuretxEngine> engineSupplier;

    public PuretxClientHttpRequestInterceptor(final PuretxEngine engine) {
        this(() -> engine);
    }

    /**
     * Resolved on first use. Bean post-processors have to be built before the engine exists,
     * so they hand over a supplier rather than forcing it into existence too early.
     */
    public PuretxClientHttpRequestInterceptor(final Supplier<PuretxEngine> engineSupplier) {
        this.engineSupplier = SingletonSupplier.of(engineSupplier);
    }

    /** Adds this interceptor to {@code restTemplate}, first in the chain, unless one is already there. */
    public void installOn(final RestTemplate restTemplate) {
        final List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (interceptors.stream().noneMatch(PuretxClientHttpRequestInterceptor.class::isInstance)) {
            interceptors.add(0, this);
        }
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution) throws IOException {
        final Detection detection =
                engineSupplier.get().start(ViolationType.HTTP_CALL, () -> summarize(request));
        if (detection == null) {
            return execution.execute(request, body);
        }
        try {
            return execution.execute(request, body);
        } finally {
            engineSupplier.get().finish(detection);
        }
    }

    /**
     * Only consulted by {@code RestTemplate}: {@code InterceptingHttpAccessor.setInterceptors}
     * sorts what it is given. Neither the list mutated by {@link #installOn} nor a
     * {@code RestClient} chain is sorted, so being first there is {@code add(0, ...)}'s doing,
     * not this. It is kept so that a template configured through {@code setInterceptors} still
     * ends up with puretx outermost.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    static String summarize(final HttpRequest request) {
        return "HTTP " + request.getMethod() + " " + request.getURI();
    }
}
