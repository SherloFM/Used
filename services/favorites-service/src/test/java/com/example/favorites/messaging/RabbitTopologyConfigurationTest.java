package com.example.favorites.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitTopologyConfigurationTest {

    private final Collection<Declarable> declarables =
            new RabbitTopologyConfiguration().listingEventsTopology().getDeclarables();

    @Test
    void theQueueIsQuorumAndDeadLettersToThisServicesKey() {
        Queue queue = queue(RabbitTopology.LISTING_EVENTS_QUEUE);

        assertTrue(queue.isDurable());
        assertEquals("quorum", queue.getArguments().get("x-queue-type"));
        assertEquals(RabbitTopology.EVENT_DLX, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitTopology.LISTING_EVENTS_DEAD_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void theDeadLetterQueueExpiresAndCapsItself() {
        Queue dlq = queue(RabbitTopology.LISTING_EVENTS_DLQ);

        assertEquals("quorum", dlq.getArguments().get("x-queue-type"));
        assertEquals(RabbitTopology.DLQ_MESSAGE_TTL_MILLIS,
                ((Number) dlq.getArguments().get("x-message-ttl")).intValue());
        assertEquals(RabbitTopology.DLQ_MAX_LENGTH,
                ((Number) dlq.getArguments().get("x-max-length")).longValue());
        assertEquals("drop-head", dlq.getArguments().get("x-overflow"));
    }

    @Test
    void theConsumerBindsExactlyTheThreeLifecycleKeys() {
        List<String> keys = declarables.stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .filter(binding -> RabbitTopology.LISTING_EVENTS_QUEUE
                        .equals(binding.getDestination()))
                .map(Binding::getRoutingKey)
                .sorted()
                .toList();

        assertEquals(List.of(RabbitTopology.DELISTED_KEY, RabbitTopology.SOLD_KEY,
                RabbitTopology.UPDATED_KEY), keys);
    }

    @Test
    void theExchangeIsTheProducersDurableTopicExchange() {
        Exchange events = exchange(RabbitTopology.EVENT_EXCHANGE);

        assertEquals("topic", events.getType());
        assertTrue(events.isDurable());
    }

    private Queue queue(String name) {
        return declarables.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> name.equals(queue.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing queue " + name));
    }

    private Exchange exchange(String name) {
        return declarables.stream()
                .filter(Exchange.class::isInstance)
                .map(Exchange.class::cast)
                .filter(exchange -> name.equals(exchange.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing exchange " + name));
    }
}
