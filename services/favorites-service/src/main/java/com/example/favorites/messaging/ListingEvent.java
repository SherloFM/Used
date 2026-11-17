package com.example.favorites.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Favorites' own copy of Marketplace's listing event.
 *
 * <p>The copy is deliberate: a change to Marketplace's record must not break
 * this service's compile, and the class never crosses a process boundary (the
 * wire carries the logical type id "listing.event").</p>
 */
public record ListingEvent(
        String eventId,
        Type type,
        Long listingId,
        Long sellerId,
        String title,
        Double price,
        String status,
        String imgUrl,
        List<String> changedFields,
        String reason,
        Source source,
        Instant updatedAt,
        Instant occurredAt
) {
    public enum Type { CREATED, UPDATED, SOLD, DELISTED }

    public enum Source { OWNER, ADMIN, MODERATION, PAYMENT, COD, LEGACY_BUY }

    public ListingEvent {
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }
}
