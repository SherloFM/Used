package com.example.marketplace.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Accounts internal contract. Only these four fields cross the
 * service boundary; profiles, passwords and codes never do.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(Long id, String username, String email, String role) {
}
