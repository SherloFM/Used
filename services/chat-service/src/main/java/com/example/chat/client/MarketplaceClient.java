package com.example.chat.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

@Component
public class MarketplaceClient {
    private final RestClient http;

    public MarketplaceClient(@Qualifier("marketplaceRestClient") RestClient http) {
        this.http = http;
    }

    public Optional<ListingSummary> find(Long listingId) {
        if (listingId == null) {
            return Optional.empty();
        }
        return http.get().uri("/api/internal/listings/{id}", listingId)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return Optional.<ListingSummary>empty();
                    }
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("Marketplace lookup failed with status " + status);
                    }
                    return Optional.ofNullable(response.bodyTo(ListingSummary.class));
                });
    }
}
