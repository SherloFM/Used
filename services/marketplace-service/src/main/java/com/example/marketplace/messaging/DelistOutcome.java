package com.example.marketplace.messaging;

/** What Marketplace decided about a delist request. */
public enum DelistOutcome {
    /** Listing was active and is now delisted by this request. */
    DELISTED,
    /** Listing was already delisted; no email, audit, or favorites work repeated. */
    ALREADY_DELISTED,
    /** The listing does not exist. Permanent refusal. */
    NOT_FOUND,
    /** The listing is sold, so it cannot be delisted. Permanent refusal. */
    SOLD,
    /** Marketplace failed transiently; the request should be retried. */
    FAILED
}
