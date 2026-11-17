package com.example.marketplace.model.responses;

public record ListingSummaryResponse(
        Long id, Long sellerId, String title,
        Double price, String status, String imgUrl
) {}
