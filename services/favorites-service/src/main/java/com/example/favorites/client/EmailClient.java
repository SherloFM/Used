package com.example.favorites.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EmailClient {
    private final RestClient http;

    public EmailClient(@Qualifier("emailRestClient") RestClient http) {
        this.http = http;
    }

    public void sendListingUnavailableEmail(String to, String listingTitle, String reason) {
        http.post().uri("/api/internal/email/listing-unavailable")
                .body(new EmailRequests.ListingUnavailableEmail(to, listingTitle, reason))
                .retrieve().toBodilessEntity();
    }
}
