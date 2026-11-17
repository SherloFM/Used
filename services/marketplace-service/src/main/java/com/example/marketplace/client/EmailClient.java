package com.example.marketplace.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class EmailClient {

    private final RestClient http;

    public EmailClient(RestClient emailRestClient) {
        this.http = emailRestClient;
    }

    public void sendCodRequestEmail(String sellerEmail, String listingTitle, String buyerName, BigDecimal amount) {
        http.post().uri("/api/internal/email/cod-request")
                .body(new EmailRequests.CodRequestEmail(sellerEmail, listingTitle, buyerName, amount))
                .retrieve().toBodilessEntity();
    }

    public void sendCodAcceptedEmail(String buyerEmail, String listingTitle) {
        http.post().uri("/api/internal/email/cod-accepted")
                .body(new EmailRequests.ListingEmailRequest(buyerEmail, listingTitle))
                .retrieve().toBodilessEntity();
    }

    public void sendCodDeclinedEmail(String buyerEmail, String listingTitle) {
        http.post().uri("/api/internal/email/cod-declined")
                .body(new EmailRequests.ListingEmailRequest(buyerEmail, listingTitle))
                .retrieve().toBodilessEntity();
    }
}
