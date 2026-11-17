package com.example.marketplace.messaging;

/** What Marketplace reports back to Moderation for one delist request. */
public record ListingDelistResult(Long reportId, DelistOutcome outcome, String message) {

    public ListingDelistResult {
        if (reportId == null || reportId <= 0) {
            throw new IllegalArgumentException("reportId must be positive");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
    }

    /** True when the listing is no longer available to buyers. */
    public boolean succeeded() {
        return outcome == DelistOutcome.DELISTED || outcome == DelistOutcome.ALREADY_DELISTED;
    }
}
