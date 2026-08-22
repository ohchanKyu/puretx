package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.Detection;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Catches {@code WebClient} calls made inside a transaction.
 *
 * <p>The check runs inside {@code Mono.defer}, so it happens at subscription time on whichever
 * thread actually triggers the call. For the common {@code webClient...block()} inside a
 * {@code @Transactional} method that is the caller's thread, transaction and all — which is the
 * case worth catching, because blocking on a remote call is what holds the connection.
 *
 * <p>A call assembled inside a transaction but subscribed later, elsewhere, is not reported:
 * by then the transaction is genuinely no longer being held.
 *
 * <p>Detection is synchronous — {@code FAIL} still throws on the caller's thread — but the
 * violation is <em>recorded</em> when the exchange terminates, which is whichever thread the
 * client completes on. A blocking caller can therefore return a moment before the report lands.
 */
public final class PuretxExchangeFilterFunction implements ExchangeFilterFunction, Ordered {

    private final PuretxEngine engine;

    public PuretxExchangeFilterFunction(final PuretxEngine engine) {
        this.engine = engine;
    }

    @Override
    public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
        return Mono.defer(() -> {
            final Detection detection = engine.start(
                    ViolationType.HTTP_CALL, () -> "HTTP " + request.method() + " " + request.url());
            if (detection == null) {
                return next.exchange(request);
            }
            return next.exchange(request).doFinally(signal -> engine.finish(detection));
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
