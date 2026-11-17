package com.example.marketplace.messaging;

/**
 * Command sent to Marketplace when an admin asks to delist a listing.
 * The acting admin travels with the command so the Marketplace audit trail
 * stays correct even though the call is asynchronous.
 */
public record ListingDelistRequested(
        Long reportId,
        Long listingId,
        Long adminId,
        String adminUsername,
        String reason) {

    public ListingDelistRequested {
        if (reportId == null || reportId <= 0) {
            throw new IllegalArgumentException("reportId must be positive");
        }
        if (listingId == null || listingId <= 0) {
            throw new IllegalArgumentException("listingId must be positive");
        }
        if (adminId == null || adminId <= 0) {
            throw new IllegalArgumentException("adminId must be positive");
        }
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("adminUsername is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }
}
