package com.acme.orders;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * A helper between the service and the HTTP client.
 *
 * <p>It is here on purpose: it makes the call indirect, which is exactly the shape a class-level
 * dependency rule cannot see. puretx should still report {@code OrderService} as the call site.
 */
@Component
public class PaymentClient {

    private final RestTemplate restTemplate;

    public PaymentClient(final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String charge(final String url) {
        return restTemplate.getForObject(url, String.class);
    }
}
