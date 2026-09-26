package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionProbe;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Answers "is this thread inside a transaction that puretx should police?" for Spring.
 *
 * <p>The interesting part is what it refuses to report. {@code isActualTransactionActive()} is still
 * true while Spring runs {@code afterCommit} callbacks and {@code @TransactionalEventListener(AFTER_COMMIT)}
 * handlers — the very place puretx tells people to move their HTTP calls and their message publishing to.
 * Flagging that would make the library useless, so the scope tracks the post-commit window explicitly.
 *
 * <p>The other way round exists too. A manager running with {@code SYNCHRONIZATION_NEVER} —
 * {@code KafkaTransactionManager}'s default — never sets {@code isActualTransactionActive()} at
 * all, so that flag alone would make every Kafka transaction invisible. For a scope that took no
 * synchronization, the question becomes whether its transaction is still bound to this thread,
 * and a manager answers that by binding its own resource: the producer factory, the data source,
 * the entity manager factory. Suspending the transaction ({@code NOT_SUPPORTED}, {@code NEVER})
 * unbinds it again, which keeps that case quiet. It has to be the manager's own key: "any
 * resource at all" is wrong, because Spring Boot's open-in-view keeps an entity manager bound
 * for the whole request, transaction or not.
 */
public final class SpringTransactionProbe implements TransactionProbe {

    @Override
    public @Nullable TransactionInfo currentTransaction() {
        final TransactionScope scope = TransactionScopeManager.current();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (scope != null) {
                return scope.isPostCompletion() ? null : scope.snapshot();
            }
            return new TransactionInfo(
                    TransactionSynchronizationManager.getCurrentTransactionName(),
                    -1,
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    false,
                    "");
        }
        if (scope != null && !scope.isSynchronised() && !scope.isFinished()) {
            final Object resourceKey = scope.resourceKey();
            if (resourceKey != null && TransactionSynchronizationManager.hasResource(resourceKey)) {
                return scope.snapshot();
            }
        }
        return null;
    }
}
