package com.example.favorites.client;

public final class EmailRequests {
    private EmailRequests() {}

    public record ListingUnavailableEmail(String to, String listingTitle, String reason) {}
}
