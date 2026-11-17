package com.example.marketplace.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the event only after the change committed.
 *
 * <p>fallbackExecution is false on purpose: an event published outside a
 * transaction is a programming error, not something to send anyway.
 * {@link ListingLifecycle} refuses that case and logs it, so this listener
 * never has to guess whether the database change survived.</p>
 */
@Component
public class ListingEventDispatcher {

    private final ListingEventPublisher publisher;

    public ListingEventDispatcher(ListingEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onListingEvent(ListingEvent event) {
        // The publisher does not block: the broker's answer is logged from the
        // confirmation future, so nothing here waits or holds a connection.
        publisher.publish(event);
    }
}
