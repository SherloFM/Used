package com.example.marketplace.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A fact about a listing that has already happened.
 *
 * <p>It is self-contained on purpose: a consumer must never call Marketplace
 * back to render its own notification. Everything the consumers need to say is
 * captured here at publish time.</p>
 */
public record ListingEvent(
        String eventId,               // UUID; also the AMQP messageId
        Type type,
        Long listingId,
        Long sellerId,
        String title,
        Double price,
        String status,                // ACTIVE / SOLD / DELISTED
        String imgUrl,
        List<String> changedFields,   // UPDATED only, otherwise empty
        String reason,                // "sold" / "delisted", or the admin reason
        Source source,
        /** The listing row's own last-modified time, i.e. what the seller sees. */
        Instant updatedAt,
        /** When this event was produced. This is the ordering token. */
        Instant occurredAt
) {
    public enum Type { CREATED, UPDATED, SOLD, DELISTED }

    /**
     * Who caused the change. It decides behaviour downstream: only an ADMIN or
     * MODERATION delist emails the seller; an owner cancellation does not.
     */
    public enum Source { OWNER, ADMIN, MODERATION, PAYMENT, COD, LEGACY_BUY }

    public ListingEvent {
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }

    public static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
