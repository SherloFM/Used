package com.example.marketplace.messaging.payment;

import com.example.marketplace.service.PaymentJobRunner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestsListener {
    private final PaymentJobRunner runner;

    public PaymentRequestsListener(PaymentJobRunner runner) {
        this.runner = runner;
    }

    @KafkaListener(topics = PaymentKafkaConfiguration.TOPIC,
            groupId = "marketplace-payment-workers",
            containerFactory = "paymentKafkaListenerContainerFactory")
    public void receive(ConsumerRecord<String, PaymentRequested> record, Acknowledgment acknowledgment) {
        PaymentRequested request = record.value();
        if (request == null || !request.listingId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Missing payment request or listing key mismatch");
        }
        // The container itself runs on paymentExecutor. No detached future or timeout.
        runner.process(request);
        acknowledgment.acknowledge();
    }
}
