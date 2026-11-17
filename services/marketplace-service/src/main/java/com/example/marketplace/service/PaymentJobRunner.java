package com.example.marketplace.service;

import com.example.marketplace.messaging.payment.PaymentRequested;
import com.example.marketplace.model.Payment;
import com.example.marketplace.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;

/** Coordinator intentionally has no transaction: worker methods use separate proxies. */
@Service
public class PaymentJobRunner {
    private static final Logger log = LoggerFactory.getLogger(PaymentJobRunner.class);
    private final PaymentRepository payments;
    private final PaymentWorker worker;

    public PaymentJobRunner(PaymentRepository payments, PaymentWorker worker) {
        this.payments = payments;
        this.worker = worker;
    }

    public void process(PaymentRequested request) {
        Payment payment = load(request.paymentId());
        if (!payment.getListing().getId().equals(request.listingId())
                || (payment.getMethod() != null && payment.getMethod() != Payment.Method.WALLET)) {
            throw new IllegalArgumentException("Payment listing or method mismatch");
        }
        if (terminal(payment)) {
            return;
        }
        try {
            worker.executePayment(request.paymentId());
        } catch (PaymentWorker.PaymentFailedException failure) {
            if ("ALREADY_HANDLED".equals(failure.getReason())) {
                if (!terminal(load(request.paymentId()))) {
                    throw new TransientDataAccessResourceException("Payment claim is still active");
                }
            } else {
                worker.markFailed(request.paymentId(), failure.getReason());
            }
            return;
        }
        try {
            worker.onPaymentCompleted(request.paymentId());
        } catch (RuntimeException failure) {
            // A failed best-effort audit must not undo/repeat a committed purchase.
            log.error("Payment {} committed but audit callback failed", request.paymentId(), failure);
        }
    }

    private Payment load(Long id) {
        return payments.findById(id)
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + id));
    }

    private boolean terminal(Payment payment) {
        return payment.getStatus() == Payment.Status.COMPLETED || payment.getStatus() == Payment.Status.FAILED;
    }
}
