package com.example.marketplace.messaging.payment;

import com.example.marketplace.model.Payment;
import com.example.marketplace.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PaymentDispatchRecovery {
    private final PaymentRepository payments;
    private final PaymentRequestPublisher publisher;
    private final int batchSize;
    private final long staleSeconds;
    private long cursor;

    public PaymentDispatchRecovery(PaymentRepository payments, PaymentRequestPublisher publisher,
            @Value("${payment.kafka.recovery-batch-size:100}") int batchSize,
            @Value("${payment.kafka.recovery-stale-seconds:120}") long staleSeconds) {
        if (batchSize < 1 || staleSeconds < 1) {
            throw new IllegalArgumentException("Recovery batch size and stale seconds must be positive");
        }
        this.payments = payments;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.staleSeconds = staleSeconds;
    }

    @Scheduled(fixedDelayString = "${payment.kafka.recovery-interval-ms:60000}",
            initialDelayString = "${payment.kafka.recovery-interval-ms:60000}")
    public synchronized void recover() {
        var batch = payments.findPendingDispatchBatch(Payment.Status.PENDING, Payment.Method.WALLET,
                LocalDateTime.now().minusSeconds(staleSeconds), cursor, PageRequest.of(0, batchSize));
        for (Object[] row : batch) {
            Long paymentId = (Long) row[0];
            publisher.publish(new PaymentRequested(1, paymentId, (Long) row[1]));
            cursor = paymentId;
        }
        if (batch.size() < batchSize) {
            cursor = 0;
        }
    }
}
