package com.example.notification.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Notification's own copy of Marketplace's listing event. Nothing here calls
 * Marketplace back: the event carries everything the message needs.
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
