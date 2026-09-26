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

    /** Writes a row in its own transaction, then holds that transaction open past the limit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAndLingerInNewTransaction(final long millis) {
        jdbcTemplate.execute("create table if not exists recorded_orders (item varchar(64))");
        jdbcTemplate.update("insert into recorded_orders (item) values ('inner')");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void checkOutsideTransaction(final String url) {
        paymentClient.charge(url);
    }
}
