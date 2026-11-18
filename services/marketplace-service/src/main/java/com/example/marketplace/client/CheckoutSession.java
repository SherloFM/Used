package com.example.marketplace.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One Almotawaj Wallet checkout session, as returned by the gateway.
 *
 * Dates stay strings: Marketplace never compares them, and a timestamp format
 * difference must not turn a paid order into an error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutSession(
        String id,
        String orderReference,
        BigDecimal amount,
        String description,
        String returnUrl,
        String status,
        String payerName,
        String checkoutUrl,
        String expiresAt,
        String paidAt,
        String refundedAt,
        String createdAt) {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final String REFUNDED = "REFUNDED";
}
