package com.example.marketplace.messaging.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentRequestPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentRequestPublisher.class);
    private final KafkaTemplate<Object, Object> kafka;

    public PaymentRequestPublisher(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    public void publishAfterCommit(PaymentRequested request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(request);
                }
            });
        } else {
            // Repository save has committed before PaymentService invokes us.
            publish(request);
        }
    }

    public void publish(PaymentRequested request) {
        try {
            kafka.send(PaymentKafkaConfiguration.TOPIC, request.listingId().toString(), request)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Payment {} dispatch failed; pending recovery will retry",
                                    request.paymentId(), failure);
                        } else {
                            log.info("Payment {} dispatched to partition {} offset {}",
                                    request.paymentId(), result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException failure) {
            log.error("Payment {} dispatch unavailable; pending recovery will retry", request.paymentId(), failure);
        }
    }
}
