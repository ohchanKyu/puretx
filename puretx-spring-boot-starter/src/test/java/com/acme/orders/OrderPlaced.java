package com.acme.orders;

/** Domain event used to exercise the {@code @TransactionalEventListener} phases. */
public record OrderPlaced(String callbackUrl) {
}
