package com.example.marketplace.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitConfigurationTest {

    private final Declarables declarables = new RabbitTopologyConfiguration().listingTopology();

    @Test
    void delistQueueIsQuorumWithDeadLettering() {
        Queue queue = queue(RabbitTopology.DELIST_QUEUE);

        assertTrue(queue.isDurable());
        assertEquals("quorum", queue.getArguments().get("x-queue-type"));
        assertEquals(RabbitTopology.COMMAND_DLX, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitTopology.DELIST_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void resultQueueIsQuorumWithDeadLettering() {
        Queue queue = queue(RabbitTopology.RESULT_QUEUE);

        assertTrue(queue.isDurable());
        assertEquals("quorum", queue.getArguments().get("x-queue-type"));
        assertEquals(RabbitTopology.EVENT_DLX, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitTopology.RESULT_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void deadLetterQueuesAreQuorumAndExpireOldMessages() {
        for (String name : new String[]{RabbitTopology.DELIST_DLQ, RabbitTopology.RESULT_DLQ}) {
            Queue dlq = queue(name);

            assertTrue(dlq.isDurable());
            assertEquals("quorum", dlq.getArguments().get("x-queue-type"));
            assertEquals(RabbitTopology.DLQ_MESSAGE_TTL_MILLIS,
                    ((Number) dlq.getArguments().get("x-message-ttl")).intValue());
            assertEquals(RabbitTopology.DLQ_MAX_LENGTH,
                    ((Number) dlq.getArguments().get("x-max-length")).longValue());
            assertEquals("drop-head", dlq.getArguments().get("x-overflow"));
        }
    }

    @Test
    void bindingsConnectTheExpectedExchanges() {
        Binding delist = binding(RabbitTopology.DELIST_QUEUE);
        assertEquals(RabbitTopology.COMMAND_EXCHANGE, delist.getExchange());
        assertEquals(RabbitTopology.DELIST_ROUTING_KEY, delist.getRoutingKey());

        Binding result = binding(RabbitTopology.RESULT_QUEUE);
        assertEquals(RabbitTopology.EVENT_EXCHANGE, result.getExchange());
        assertEquals(RabbitTopology.RESULT_ROUTING_KEY, result.getRoutingKey());

        assertEquals("direct", exchange(RabbitTopology.COMMAND_EXCHANGE).getType());
        assertEquals("direct", exchange(RabbitTopology.COMMAND_DLX).getType());
        assertEquals("topic", exchange(RabbitTopology.EVENT_EXCHANGE).getType());
        assertEquals("direct", exchange(RabbitTopology.EVENT_DLX).getType());
        assertTrue(exchange(RabbitTopology.EVENT_EXCHANGE).isDurable());
    }

    @Test
    void templatePublishesMandatorily() {
        RabbitTemplate template = new RabbitTemplate();
        new RabbitConfiguration().rabbitTemplateCustomizer().customize(template);

        assertTrue(template.isMandatoryFor(new Message(new byte[0])));
    }

    private Collection<Declarable> all() {
        return declarables.getDeclarables();
    }

    private Queue queue(String name) {
        return all().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> name.equals(queue.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing queue " + name));
    }

    private Exchange exchange(String name) {
        return all().stream()
                .filter(Exchange.class::isInstance)
                .map(Exchange.class::cast)
                .filter(exchange -> name.equals(exchange.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing exchange " + name));
    }

    private Binding binding(String destination) {
        return all().stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .filter(binding -> destination.equals(binding.getDestination()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing binding for " + destination));
    }
}
