package com.example.Used.Model.Responses;

import com.example.Used.Model.Payment;

import java.time.LocalDateTime;

/** Polling answer: "is my payment done yet?" */
public class PaymentStatus {

    public Long paymentId;
    public Long listingId;
    public Payment.Status status;
    public String failureReason;
    public String amount;
    public LocalDateTime processedAt;

    public static PaymentStatus from(Payment p) {
        PaymentStatus s = new PaymentStatus();
        s.paymentId = p.getId();
        s.listingId = p.getListing().getId();
        s.status = p.getStatus();
        s.failureReason = p.getFailureReason();
        s.amount = p.getAmount().toPlainString();
        s.processedAt = p.getProcessedAt();
        return s;
    }
}
