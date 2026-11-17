package com.example.favorites.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListingSummary(Long id, Long sellerId, String title,
                             Double price, String status, String imgUrl) {}
