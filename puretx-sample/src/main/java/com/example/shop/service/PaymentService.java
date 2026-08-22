package com.example.shop.service;

import com.example.shop.controller.FakePaymentProviderController;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** Stands in for a real payment provider. Slow, like real ones are. */
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Set by Spring Boot once the server is listening; the only accurate source under a random port. */
    private static final String LOCAL_SERVER_PORT_PROPERTY = "local.server.port";

    private static final String SERVER_PORT_PROPERTY = "server.port";

    private static final String DEFAULT_PORT = "8080";

    private static final String CHARGE_URL_FORMAT = "http://localhost:%s" + FakePaymentProviderController.CHARGE_PATH;

    private final RestTemplate restTemplate;

    private final Environment environment;

    public void charge(final long orderId) {
        restTemplate.postForObject(chargeUrl(), orderId, String.class);
    }

    /** Resolved per call: under a random test port the real one is only known once the server is up. */
    private String chargeUrl() {
        final String port = environment.getProperty(
                LOCAL_SERVER_PORT_PROPERTY,
                environment.getProperty(SERVER_PORT_PROPERTY, DEFAULT_PORT)
        );
        return CHARGE_URL_FORMAT.formatted(port);
    }
}
