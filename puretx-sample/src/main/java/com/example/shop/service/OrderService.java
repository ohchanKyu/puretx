package com.example.shop.service;

import com.example.shop.event.OrderEvent;
import com.example.shop.storage.Order;
import com.example.shop.storage.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final PaymentService paymentService;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * The version puretx complains about.
     *
     * <p>The database connection and every lock this transaction holds stay taken for as long as
     * the payment provider feels like taking. Under load that is how a connection pool runs dry.
     */
    @Transactional
    public long placeOrder(final String item) {
        final Order newOrder = orderRepository.save(Order.from(item));
        paymentService.charge(newOrder.getId());
        return newOrder.getId();
    }

    /**
     * The same thing, with the payment moved out of the transaction entirely.
     *
     * <p>The service publishes a fact and stops there; {@code PaymentEventListener} does the
     * charging once the commit has actually happened. The transaction now lasts as long as an
     * insert.
     *
     * <p>Whether losing the charge on a crash between commit and listener is acceptable is a
     * question about this business, not about transactions — and it is the question puretx wants
     * somebody to actually ask. If the answer is no, the next step is an outbox, which is
     * deliberately not puretx's job.
     */
    @Transactional
    public long placeOrderCleanly(final String item) {
        final Order newOrder = orderRepository.save(Order.from(item));
        eventPublisher.publishEvent(new OrderEvent(newOrder.getId()));
        return newOrder.getId();
    }
}
