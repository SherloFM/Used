package com.example.marketplace.messaging.payment;

public record PaymentRequested(int version, Long paymentId, Long listingId) {
    public PaymentRequested {
        if (version != 1 || paymentId == null || paymentId <= 0
                || listingId == null || listingId <= 0) {
            throw new IllegalArgumentException("Invalid payment request version or IDs");
        }
    }
}
