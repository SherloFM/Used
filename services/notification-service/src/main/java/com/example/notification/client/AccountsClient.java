package com.example.notification.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Read-only access to Accounts user summaries, used to find the seller's email.
 *
 * <p>404 means "no such user" and is a normal result. Anything else that is not
 * 2xx is an incident: it fails loudly rather than silently skipping an email.</p>
 */
@Component
public class AccountsClient {

    private final RestClient http;

    public AccountsClient(RestClient accountsRestClient) {
        this.http = accountsRestClient;
    }

    public Optional<UserSummary> find(Long userId) {

        if (userId == null) {
            return Optional.empty();
        }

        return http.get()
                .uri("/api/internal/users/{id}", userId)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return Optional.<UserSummary>empty();
                    }
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("Accounts lookup failed with status " + status);
                    }
                    return Optional.ofNullable(response.bodyTo(UserSummary.class));
                });
    }
}
