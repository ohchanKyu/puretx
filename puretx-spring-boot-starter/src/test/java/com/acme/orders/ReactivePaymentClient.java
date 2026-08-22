package com.acme.orders;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** The same remote call over WebClient, blocked on — the shape that actually holds the connection. */
@Component
public class ReactivePaymentClient {

    private final WebClient webClient;

    public ReactivePaymentClient(final WebClient webClient) {
        this.webClient = webClient;
    }

    public String charge(final String url) {
        return webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
    }
}
