package com.example.marketplace.model.responses;

import com.example.marketplace.model.Payment;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentStatus {
    private Long id;
    private Long listingId;
    private String listingTitle;
    private String status;
    private String method;
    private String failureReason;
    private String friendlyMessage;
    private int queuePosition;
    private LocalDateTime expiresAt;
    private String amount;

    public static PaymentStatus from(Payment p) {
        return from(p, 1);
    }

    public static PaymentStatus from(Payment p, int queuePosition) {
        PaymentStatus s = new PaymentStatus();
        s.id = p.getId();
        s.listingId = p.getListing() != null ? p.getListing().getId() : null;
        s.listingTitle = p.getListing() != null ? p.getListing().getTitle() : null;
        s.status = p.getStatus().name();
        s.method = p.getMethod() != null ? p.getMethod().name() : "WALLET";
        s.failureReason = p.getFailureReason();
        s.friendlyMessage = friendly(p);
        s.queuePosition = queuePosition;
        s.expiresAt = p.getExpiresAt();
        s.amount = p.getAmount() != null ? p.getAmount().toPlainString() : null;
        return s;
    }

    private static String friendly(Payment p) {
        if (p.getStatus() == Payment.Status.FAILED) {
            return switch (p.getFailureReason() == null ? "" : p.getFailureReason()) {
                case "COD_EXPIRED" -> "Your cash-on-delivery request expired because the seller didn't respond in time.";
                case "COD_DECLINED" -> "The seller declined your cash-on-delivery request.";
                case "ALREADY_SOLD" -> "This item was sold to another buyer.";
                case "INSUFFICIENT_FUNDS" -> "Insufficient wallet balance.";
                case "GATEWAY_EXPIRED" -> "The wallet checkout expired before it was paid. Please start it again.";
                case "GATEWAY_CANCELLED" -> "The wallet checkout was cancelled. Please start it again.";
                case "GATEWAY_REFUNDED" -> "The wallet payment was refunded.";
                default -> "Something went wrong. Please try again.";
            };
        }
        if (p.getStatus() == Payment.Status.COMPLETED) return "Payment completed.";
        if (p.getStatus() == Payment.Status.PENDING) {
            return p.getMethod() == Payment.Method.CARD
                    ? "Waiting for the Almotawaj Wallet payment to be confirmed."
                    : "Waiting for seller approval.";
        }
        return "Processing...";
    }
}
