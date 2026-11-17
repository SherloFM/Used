package com.example.marketplace.client;

import java.math.BigDecimal;

public final class EmailRequests {
    private EmailRequests() {
    }

    public record ListingEmailRequest(String to, String listingTitle) {
    }

    public record CodRequestEmail(String sellerEmail, String listingTitle, String buyerName, BigDecimal amount) {
    }
}
