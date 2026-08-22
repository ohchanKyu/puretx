package com.example.shop.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "remote" system, hosted in the same process so the sample needs nothing else running.
 *
 * <p>Its latency is deliberate: it must exceed {@code puretx.max-duration} (300ms in
 * {@code application.yml}) so that the impure endpoint produces both violations — the HTTP call
 * and the transaction it held open waiting for it.
 */
@RestController
@RequestMapping(FakePaymentProviderController.BASE_PATH)
public class FakePaymentProviderController {

    public static final String BASE_PATH = "/fake-payment-provider";

    public static final String CHARGE_SUB_PATH = "/charge";

    /** The full path, for callers. Kept here so the client and the endpoint cannot drift apart. */
    public static final String CHARGE_PATH = BASE_PATH + CHARGE_SUB_PATH;

    /** Comfortably above {@code puretx.max-duration}, so the long-transaction violation fires too. */
    private static final long PROVIDER_LATENCY_MILLIS = 400L;

    private static final String CHARGED = "charged";

    @PostMapping(CHARGE_SUB_PATH)
    public String charge() throws InterruptedException {
        sleep();
        return CHARGED;
    }

    private void sleep() throws InterruptedException {
        Thread.sleep(PROVIDER_LATENCY_MILLIS);
    }
}
