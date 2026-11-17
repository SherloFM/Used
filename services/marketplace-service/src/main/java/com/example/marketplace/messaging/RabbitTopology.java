package com.example.marketplace.messaging;

public final class RabbitTopology {

    /** Commands sent to Marketplace. Direct, because each command has one owner. */
    public static final String COMMAND_EXCHANGE = "listing.commands";
    public static final String DELIST_QUEUE = "marketplace.listing-delist";
    public static final String DELIST_ROUTING_KEY = "listing.delist";
    public static final String COMMAND_DLX = "listing.commands.dlx";
    public static final String DELIST_DLQ = "marketplace.listing-delist.dlq";

    /** Facts emitted by Marketplace. Topic, so lifecycle events can be added later. */
    public static final String EVENT_EXCHANGE = "listing.events";
    public static final String RESULT_QUEUE = "moderation.listing-delist-results";
    public static final String RESULT_ROUTING_KEY = "listing.delist.result";
    public static final String EVENT_DLX = "listing.events.dlx";
    public static final String RESULT_DLQ = "moderation.listing-delist-results.dlq";

    /**
     * Lifecycle facts. One routing key per event type, so a consumer binds only
     * the types it cares about and the broker does the filtering.
     *
     * CREATED is defined but not published yet: no consumer needs it, and with
     * mandatory publishing an unroutable create would log an error on every
     * listing. Also defined: AUDIT could be a future consumer.
     */
    public static final String CREATED_KEY = "listing.created";
    public static final String UPDATED_KEY = "listing.updated";
    public static final String SOLD_KEY = "listing.sold";
    public static final String DELISTED_KEY = "listing.delisted";

    /**
     * Logical type ids carried in __TypeId__. Never Java class names: each
     * service owns its own copy of every contract, so a class name from another
     * service would be refused as untrusted or fail as a missing class.
     */
    public static final String TYPE_LISTING_EVENT = "listing.event";
    public static final String TYPE_DELIST_COMMAND = "listing.delist.command";
    public static final String TYPE_DELIST_RESULT = "listing.delist.result";

    /**
     * Dead-letter queues are for inspecting failures, not for keeping history.
     * After seven days a message is dropped, even if nobody looked at it.
     */
    public static final int DLQ_MESSAGE_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000;

    /** And only the newest 10,000 are kept; older ones are dropped first. */
    public static final long DLQ_MAX_LENGTH = 10_000;

    private RabbitTopology() {
    }
}
