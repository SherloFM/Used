package com.example.marketplace.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes listing lifecycle events without waiting for the broker.
 *
 * <p>The publish is handed to the broker and the method returns. The broker's
 * answer arrives later and is logged from the correlation future: a rejected
 * message, a lost connection, or a message that was confirmed but had no queue
 * to go to. Nothing blocks, so a slow broker can never hold a request thread or
 * a database connection.</p>
 *
 * <p>The price of not waiting: the caller cannot know whether the event reached
 * a queue. Durability across a lost publish needs the outbox recorded in
 * docs/architecture-decisions.md.</p>
 */
@Component
public class ListingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ListingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ListingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ListingEvent event) {

        CorrelationData correlation = new CorrelationData(event.eventId());

        // Registered first: if the confirm arrived before this line, whenComplete
        // still runs immediately, so no outcome can be missed.
        correlation.getFuture().whenComplete((confirm, failure) -> logOutcome(event, correlation, confirm, failure));

        try {
            rabbitTemplate.convertAndSend(
                    RabbitTopology.EVENT_EXCHANGE,
                    routingKeyFor(event.type()),
                    event,
                    message -> {
                        message.getMessageProperties().setMessageId(event.eventId());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlation);
        } catch (RuntimeException exception) {
            // No connection or a closed channel: no confirm will ever arrive.
            log.error("Could not send listing event {} ({})",
                    event.eventId(), event.type(), exception);
        }
    }

    private void logOutcome(ListingEvent event, CorrelationData correlation,
                            CorrelationData.Confirm confirm, Throwable failure) {

        if (failure != null) {
            log.error("No confirmation for listing event {} ({})",
                    event.eventId(), event.type(), failure);
            return;
        }
        if (confirm == null || !confirm.ack()) {
            log.error("Broker rejected listing event {} ({}): {}",
                    event.eventId(), event.type(),
                    confirm == null ? "no confirmation" : confirm.reason());
            return;
        }

        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            // Confirmed, but no queue was bound: the broker acknowledged a drop.
            log.error("Listing event {} ({}) was unroutable: {}",
                    event.eventId(), event.type(), returned.getReplyText());
            return;
        }

        log.info("Published listing event {} ({}) for listing {}",
                event.eventId(), event.type(), event.listingId());
    }

    private String routingKeyFor(ListingEvent.Type type) {
        return switch (type) {
            case CREATED -> RabbitTopology.CREATED_KEY;
            case UPDATED -> RabbitTopology.UPDATED_KEY;
            case SOLD -> RabbitTopology.SOLD_KEY;
            case DELISTED -> RabbitTopology.DELISTED_KEY;
        };
    }
}
