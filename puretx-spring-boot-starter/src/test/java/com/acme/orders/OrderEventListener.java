package com.acme.orders;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Both halves of the {@code @TransactionalEventListener} story: one safe phase, one not. */
@Component
public class OrderEventListener {

    private final PaymentClient paymentClient;

    public OrderEventListener(final PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void chargeAfterCommit(final OrderPlaced event) {
        paymentClient.charge(event.callbackUrl());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void chargeBeforeCommit(final OrderService.BeforeCommitOrderPlaced event) {
        paymentClient.charge(event.callbackUrl());
    }
}
