package com.acme.orders;

import io.github.ohchankyu.puretx.Puretx;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The application under observation. Every method here is one row in the false-positive checklist. */
@Service
public class OrderService {

    private final PaymentClient paymentClient;
    private final ReactivePaymentClient reactivePaymentClient;
    private final RestClientPaymentClient restClientPaymentClient;

    private final RetryingPaymentClient retryingPaymentClient;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher events;
    private final InventoryService inventoryService;

    public OrderService(final PaymentClient paymentClient, final ReactivePaymentClient reactivePaymentClient,
            final RestClientPaymentClient restClientPaymentClient,
            final RetryingPaymentClient retryingPaymentClient, final JdbcTemplate jdbcTemplate,
            final ApplicationEventPublisher events, final InventoryService inventoryService) {
        this.paymentClient = paymentClient;
        this.reactivePaymentClient = reactivePaymentClient;
        this.restClientPaymentClient = restClientPaymentClient;
        this.retryingPaymentClient = retryingPaymentClient;
        this.jdbcTemplate = jdbcTemplate;
        this.events = events;
        this.inventoryService = inventoryService;
    }

    /** The problem this library exists for. */
    @Transactional
    public void createOrder(final String url) {
        save();
        paymentClient.charge(url);
    }

    /** RestClient inside the transaction. Same problem, newer API. */
    @Transactional
    public void createOrderWithRestClient(final String url) {
        save();
        restClientPaymentClient.charge(url);
    }

    /** A call whose client retries internally: the transaction is held for the whole sequence. */
    @Transactional
    public void createOrderWithRetryingClient(final String url) {
        save();
        retryingPaymentClient.charge(url);
    }

    /** Reactive client, blocked on inside the transaction. Same problem, different API. */
    @Transactional
    public void createOrderWithWebClient(final String url) {
        save();
        reactivePaymentClient.charge(url);
    }

    /** WebClient with no transaction around it. */
    public void createOrderWithWebClientOutsideTransaction(final String url) {
        reactivePaymentClient.charge(url);
    }

    /** The same call with no transaction around it — must stay silent. */
    public void createOrderWithoutTransaction(final String url) {
        paymentClient.charge(url);
    }

    /** The fix puretx recommends. Reporting this would make the advice self-contradicting. */
    @Transactional
    public void createOrderChargingAfterCommit(final String url) {
        save();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paymentClient.charge(url);
            }
        });
    }

    /** The other recommended fix: an event handled after the commit. */
    @Transactional
    public void createOrderPublishingEvent(final String url) {
        save();
        events.publishEvent(new OrderPlaced(url));
    }

    /** Still inside the transaction, however it is dressed up. Must be reported. */
    @Transactional
    public void createOrderPublishingBeforeCommitEvent(final String url) {
        save();
        events.publishEvent(new BeforeCommitOrderPlaced(url));
    }

    /** An inner REQUIRES_NEW transaction, then more work back in the outer one. */
    @Transactional
    public void createOrderWithSeparateAudit(final String url) {
        save();
        inventoryService.reserveInNewTransaction(url);
        paymentClient.charge(url);
    }

    /** A call the team has looked at and decided to keep. */
    @Transactional
    public void createOrderWithAcknowledgedCall(final String url) {
        save();
        Puretx.suppress(() -> paymentClient.charge(url));
    }

    /** Holds the transaction open without doing anything else wrong. */
    @Transactional
    public void slowOrder(final long millis) {
        save();
        sleep(millis);
    }

    @Transactional
    public void failingSlowOrder(final long millis) {
        save();
        sleep(millis);
        throw new IllegalStateException("boom");
    }

    @Transactional(readOnly = true)
    public void readOnlyLookup(final String url) {
        paymentClient.charge(url);
    }

    private void save() {
        jdbcTemplate.execute("select 1");
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Published before commit, so its handler still runs inside the transaction. */
    public record BeforeCommitOrderPlaced(String callbackUrl) {
    }
}
