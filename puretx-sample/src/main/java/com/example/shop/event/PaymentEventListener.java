package com.example.shop.event;

import com.example.shop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Charges the customer once the order is safely committed.
 *
 * <p>Deliberately a separate class in a separate package. The service no longer knows that paying
 * happens at all — it says "an order was placed" and stops there, which is the whole point of
 * moving the call out of the transaction rather than merely deferring it inline.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentService paymentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void chargeAfterCommit(final OrderEvent event) {
        paymentService.charge(event.orderId());
    }
}
