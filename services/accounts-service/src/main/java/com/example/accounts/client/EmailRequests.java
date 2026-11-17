package com.example.accounts.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class EmailRequests {
    private EmailRequests() {
    }

    public record ToRequest(@Email @NotBlank String to) {
    }

    public record VerificationRequest(String to, String code) {
    }

    public record PasswordResetRequest(String to, String code) {
    }

    public record ListingEmailRequest(String to, String listingTitle) {
    }

    public record CodRequestEmail(String sellerEmail, String listingTitle, String buyerName, java.math.BigDecimal amount) {
    }

    public record ListingUnavailableEmail(String to, String listingTitle, String reason) {
    }
}
