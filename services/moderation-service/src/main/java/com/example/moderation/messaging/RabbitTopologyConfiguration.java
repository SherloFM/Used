package com.example.moderation.messaging;

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
 * Durable delist topology. Both services declare it, so declarations are
 * idempotent and either side can start first without losing messages.
 *
 * <p>Every queue is a quorum queue: replicated, and safer than a classic queue
 * for messages that represent a moderation action. Dead-letter queues expire
 * their contents instead of growing forever.</p>
 */
@Configuration
public class RabbitTopologyConfiguration {

    @Bean
    public Declarables listingTopology() {
        DirectExchange commands = new DirectExchange(RabbitTopology.COMMAND_EXCHANGE, true, false);
        DirectExchange commandDlx = new DirectExchange(RabbitTopology.COMMAND_DLX, true, false);
        Queue delist = QueueBuilder.durable(RabbitTopology.DELIST_QUEUE)
                .quorum()
                .deadLetterExchange(RabbitTopology.COMMAND_DLX)
                .deadLetterRoutingKey(RabbitTopology.DELIST_ROUTING_KEY)
                .build();
        Queue delistDlq = deadLetterQueue(RabbitTopology.DELIST_DLQ);

        TopicExchange events = new TopicExchange(RabbitTopology.EVENT_EXCHANGE, true, false);
        DirectExchange eventDlx = new DirectExchange(RabbitTopology.EVENT_DLX, true, false);
        Queue results = QueueBuilder.durable(RabbitTopology.RESULT_QUEUE)
                .quorum()
                .deadLetterExchange(RabbitTopology.EVENT_DLX)
                .deadLetterRoutingKey(RabbitTopology.RESULT_ROUTING_KEY)
                .build();
        Queue resultsDlq = deadLetterQueue(RabbitTopology.RESULT_DLQ);

        Binding delistBinding = BindingBuilder.bind(delist).to(commands)
                .with(RabbitTopology.DELIST_ROUTING_KEY);
        Binding delistDlqBinding = BindingBuilder.bind(delistDlq).to(commandDlx)
                .with(RabbitTopology.DELIST_ROUTING_KEY);
        Binding resultBinding = BindingBuilder.bind(results).to(events)
                .with(RabbitTopology.RESULT_ROUTING_KEY);
        Binding resultDlqBinding = BindingBuilder.bind(resultsDlq).to(eventDlx)
                .with(RabbitTopology.RESULT_ROUTING_KEY);

        return new Declarables(commands, commandDlx, delist, delistDlq,
                events, eventDlx, results, resultsDlq,
                delistBinding, delistDlqBinding, resultBinding, resultDlqBinding);
    }

    /**
     * A dead-letter queue keeps the newest failures and drops the oldest when it
     * is full. Combined with the TTL, unactionable messages cannot accumulate.
     */
    private Queue deadLetterQueue(String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .ttl(RabbitTopology.DLQ_MESSAGE_TTL_MILLIS)
                .maxLength(RabbitTopology.DLQ_MAX_LENGTH)
                .overflow(QueueBuilder.Overflow.dropHead)
                .build();
    }
}
