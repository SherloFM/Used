package com.example.moderation.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the delist command only after the report transaction committed.
 *
 * <p>The report is saved first and the command afterwards. If the commit rolls
 * back, the listener never runs and no command is sent, so Marketplace can never
 * delist a listing for a decision moderation did not keep.</p>
 *
 * <p>Failures are not thrown at the admin: the report is already committed as
 * DELISTING and the retry sweep owns convergence from there.</p>
 */
@Component
public class DelistCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DelistCommandDispatcher.class);

    private final ListingCommandPublisher publisher;

    public DelistCommandDispatcher(ListingCommandPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelistRequested(ListingDelistRequested command) {
        if (!publisher.requestDelist(command)) {
            log.warn("Delist command for report {} was not accepted by the broker; "
                    + "the retry sweep will publish it again", command.reportId());
        }
    }
}
