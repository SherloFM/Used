package com.example.favorites.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(Long id, String username, String email, String role) {}
