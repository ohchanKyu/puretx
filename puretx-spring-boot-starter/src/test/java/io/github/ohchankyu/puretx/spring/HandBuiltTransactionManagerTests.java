package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.PuretxIntegrationTest;
import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A transaction manager built by hand is not a bean, so puretx never instruments it and never
 * sees its transactions begin. It still runs on this context's data source, and a call inside
 * one of its transactions is this context's business.
 *
 * <p>The facade is re-installed before each test because other classes in this suite install
 * engines of their own throwaway contexts into the static facade, and the cached context's
 * installer ran only once, when the context was built.
 */
@PuretxIntegrationTest
class HandBuiltTransactionManagerTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PuretxEngine engine;

    @Autowired
    private PuretxFacadeInstaller facade;

    @BeforeEach
    void reset() {
        facade.afterSingletonsInstantiated();
        engine.store().clear();
    }

    @Test
    @DisplayName("watch inside a hand-built manager's transaction on the context's data source reports here")
    void watchReportsIntoTheContextThatOwnsTheDataSource() {
        final TransactionTemplate handBuilt = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        handBuilt.executeWithoutResult(status -> Puretx.watch("Slack chat.postMessage", () -> "sent"));

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.summary()).isEqualTo("Slack chat.postMessage"));
    }
}
