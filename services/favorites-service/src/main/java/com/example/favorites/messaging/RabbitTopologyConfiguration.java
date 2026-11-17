package com.example.favorites.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The consumer owns its queue and bindings; the producer owns the exchange.
 * Both declare the exchange idempotently, so either side can start first.
 */
@Configuration
public class RabbitTopologyConfiguration {

    @Bean
    public Declarables listingEventsTopology() {

        TopicExchange events = new TopicExchange(RabbitTopology.EVENT_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(RabbitTopology.EVENT_DLX, true, false);

        Queue queue = QueueBuilder.durable(RabbitTopology.LISTING_EVENTS_QUEUE)
                .quorum()
                .deadLetterExchange(RabbitTopology.EVENT_DLX)
                .deadLetterRoutingKey(RabbitTopology.LISTING_EVENTS_DEAD_KEY)
                .build();

        Queue deadLettersQueue = QueueBuilder.durable(RabbitTopology.LISTING_EVENTS_DLQ)
                .quorum()
                .ttl(RabbitTopology.DLQ_MESSAGE_TTL_MILLIS)
                .maxLength(RabbitTopology.DLQ_MAX_LENGTH)
                .overflow(QueueBuilder.Overflow.dropHead)
                .build();

        Binding updated = BindingBuilder.bind(queue).to(events).with(RabbitTopology.UPDATED_KEY);
        Binding sold = BindingBuilder.bind(queue).to(events).with(RabbitTopology.SOLD_KEY);
        Binding delisted = BindingBuilder.bind(queue).to(events).with(RabbitTopology.DELISTED_KEY);
        Binding dead = BindingBuilder.bind(deadLettersQueue).to(deadLetters)
                .with(RabbitTopology.LISTING_EVENTS_DEAD_KEY);

        return new Declarables(events, deadLetters, queue, deadLettersQueue,
                updated, sold, delisted, dead);
    }
}
