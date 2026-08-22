package com.acme.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A separate bean so {@code REQUIRES_NEW} actually goes through the proxy. */
@Service
public class InventoryService {

    private final PaymentClient paymentClient;
    private final JdbcTemplate jdbcTemplate;

    public InventoryService(final PaymentClient paymentClient, final JdbcTemplate jdbcTemplate) {
        this.paymentClient = paymentClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveInNewTransaction(final String url) {
        jdbcTemplate.execute("select 1");
        paymentClient.charge(url);
    }
}
