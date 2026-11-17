package com.example.marketplace.messaging;

import com.example.marketplace.model.Listings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The only way Marketplace announces a listing change.
 *
 * <p>Call it from inside the transaction that changed the listing. It does not
 * talk to RabbitMQ here: it hands the event to Spring, and
 * {@link ListingEventDispatcher} sends it after the commit. A rolled-back change
 * can therefore never be announced, and the request thread never waits for a
 * broker round trip.</p>
 *
 * <p>Because the dispatcher runs only after a commit and does not fall back, a
 * call made outside a transaction would be silently dropped. The guard below
 * makes that mistake loud instead.</p>
 */
@Component
public class ListingLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ListingLifecycle.class);

    private final ApplicationEventPublisher events;

    public ListingLifecycle(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void updated(Listings listing, List<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return;
        }
        publish(build(listing, ListingEvent.Type.UPDATED, ListingEvent.Source.OWNER,
                listing.getStatus() == null ? null : listing.getStatus().name(),
                changedFields, null));
    }

    /**
     * A sale has just been won by a successful ACTIVE -> SOLD claim. The status
     * is SOLD by definition, so it is not read from a possibly stale entity.
     */
    public void sold(Listings listing, ListingEvent.Source source) {
        publish(build(listing, ListingEvent.Type.SOLD, source,
                Listings.Status.SOLD.name(), List.of(), "sold"));
    }

    public void delisted(Listings listing, ListingEvent.Source source, String reason) {
        publish(build(listing, ListingEvent.Type.DELISTED, source,
                Listings.Status.DELISTED.name(), List.of(), reason));
    }

    private void publish(ListingEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Without a transaction there is nothing to commit, and the
            // after-commit listener would never run. Failing loudly beats an
            // event that disappears.
            log.error("Refusing to publish {} for listing {}: no active transaction",
                    event.type(), event.listingId());
            return;
        }
        events.publishEvent(event);
    }

    private ListingEvent build(Listings listing, ListingEvent.Type type,
                               ListingEvent.Source source, String status,
                               List<String> changedFields, String reason) {
        // Snapshot everything now. The entity must never be read later, after
        // the transaction ends, on the publisher thread.
        return new ListingEvent(
                ListingEvent.newEventId(),
                type,
                listing.getId(),
                listing.getUserId(),
                listing.getTitle(),
                listing.getPrice(),
                status,
                listing.getImgUrl(),
                changedFields,
                reason,
                source,
                toInstant(listing.getUpdatedAt()),
                Instant.now());
    }

    /**
     * The entity stores a LocalDateTime, written by @UpdateTimestamp. It is
     * converted with the JVM zone, which is the zone that wrote it.
     */
    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
