package com.acme.orders;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The same call again, through RestClient. */
@Component
public class RestClientPaymentClient {

    private final RestClient restClient;

    public RestClientPaymentClient(final RestClient restClient) {
        this.restClient = restClient;
    }

    public String charge(final String url) {
        return restClient.get().uri(url).retrieve().body(String.class);
    }
}
