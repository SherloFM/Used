package com.example.favorites.messaging;

/**
 * Names this consumer owns. The exchange belongs to the producer.
 */
public final class RabbitTopology {

    public static final String EVENT_EXCHANGE = "listing.events";
    public static final String EVENT_DLX = "listing.events.dlx";

    public static final String LISTING_EVENTS_QUEUE = "favorites.listing-events";
    public static final String LISTING_EVENTS_DLQ = "favorites.listing-events.dlq";

    /**
     * One dead key per queue. A queue bound to three routing keys cannot reuse
     * one of them for all dead letters, or they would be indistinguishable.
     */
    public static final String LISTING_EVENTS_DEAD_KEY = "favorites.listing-events.dead";

    public static final String UPDATED_KEY = "listing.updated";
    public static final String SOLD_KEY = "listing.sold";
    public static final String DELISTED_KEY = "listing.delisted";

    /** Logical type id on the wire. Never a Java class name. */
    public static final String TYPE_LISTING_EVENT = "listing.event";

    public static final int DLQ_MESSAGE_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000;
    public static final long DLQ_MAX_LENGTH = 10_000;

    private RabbitTopology() {
    }
}
