package com.example.marketplace.messaging.payment;

import com.example.marketplace.service.PaymentWorker;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PaymentKafkaConfiguration {
    public static final String TOPIC = "payment-requests";
    public static final String DLT = TOPIC + ".DLT";

    @Bean
    public NewTopic paymentRequestsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1)
                .config("retention.ms", "604800000").build();
    }

    @Bean
    public NewTopic paymentRequestsDeadLetterTopic() {
        // Same partition count: the default recoverer preserves the source partition.
        return TopicBuilder.name(DLT).partitions(3).replicas(1)
                .config("retention.ms", "604800000")
                .config("retention.bytes", "104857600").build();
    }

    @Bean
    public DefaultErrorHandler paymentKafkaErrorHandler(
            KafkaOperations<Object, Object> kafkaTemplate, PaymentWorker paymentWorker) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            recoverer.accept(record, exception);
            // Do not let the pending-payment recovery scan endlessly republish a
            // quarantined job. If this DB operation fails, recovery is unsuccessful
            // and the source offset stays uncommitted; duplicate DLT records are possible.
            if (record.value() instanceof PaymentRequested request
                    && request.listingId().toString().equals(record.key())) {
                paymentWorker.markDispatchFailed(request.paymentId(), request.listingId());
            }
        }, new FixedBackOff(5_000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> paymentKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler paymentKafkaErrorHandler,
            @Qualifier("paymentExecutor") ThreadPoolExecutor executor,
            @Value("${payment.kafka.concurrency:3}") int concurrency) {
        if (concurrency < 1 || concurrency > executor.getCorePoolSize()) {
            throw new IllegalArgumentException("Kafka concurrency must be between 1 and payment pool size");
        }
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(paymentKafkaErrorHandler);
        factory.getContainerProperties().setListenerTaskExecutor(new TaskExecutorAdapter(executor));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setSyncCommits(true);
        return factory;
    }
}
