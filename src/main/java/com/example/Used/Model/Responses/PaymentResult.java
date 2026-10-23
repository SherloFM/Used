package com.example.Used.Model.Responses;

import com.example.Used.Model.Payment;

import java.time.LocalDateTime;

/** Returned by POST /api/payments: tells the client whether the task was
 *  accepted, was a duplicate, and where to poll for the outcome. */
public class PaymentResult {

    public enum Outcome { ACCEPTED, DUPLICATE }

    public Long paymentId;
    public Long listingId;
    public Outcome outcome;
    public Payment.Status currentStatus;
    public String pollUrl;
    public LocalDateTime createdAt;

    static PaymentResult from(Payment p, Outcome o) {
        PaymentResult r = new PaymentResult();
        r.paymentId = p.getId();
        r.listingId = p.getListing().getId();
        r.outcome = o;
        r.currentStatus = p.getStatus();
        r.pollUrl = "/api/payments/" + p.getId();
        r.createdAt = p.getCreatedAt();
        return r;
    }

    public static PaymentResult accepted(Payment p) {
        return from(p, Outcome.ACCEPTED);
    }

    public static PaymentResult duplicate(Payment p) {
        return from(p, Outcome.DUPLICATE);
    }
}
