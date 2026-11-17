package com.example.marketplace.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The publisher must never block and never throw at the caller. */
class ListingEventPublisherTest {

    private RabbitTemplate template;
    private ListingEventPublisher publisher;

    @BeforeEach
    void setup() {
        template = mock(RabbitTemplate.class);
        publisher = new ListingEventPublisher(template);
    }

    private ListingEvent event(ListingEvent.Type type) {
        return new ListingEvent("event-1", type, 7L, 3L, "Bike", 150.0,
                type == ListingEvent.Type.SOLD ? "SOLD" : "ACTIVE", "/img/7",
                List.of("title"), "sold", ListingEvent.Source.PAYMENT,
                Instant.parse("2026-11-15T10:00:00Z"),
                Instant.parse("2026-11-15T10:15:30Z"));
    }

    /** Completes the confirm the way the broker would, after the call returns. */
    private void confirmLater(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(
                        new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", RabbitTopology.EVENT_EXCHANGE, "listing.sold"));
            }
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(ack, ack ? null : "broker refused"));
            return null;
        }).when(template).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    private MessagePostProcessor captureProcessor(ListingEvent event) {
        ArgumentCaptor<MessagePostProcessor> processor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation =
                ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(
                eq(RabbitTopology.EVENT_EXCHANGE),
                eq(RabbitTopology.SOLD_KEY),
                eq(event),
                processor.capture(),
                correlation.capture());
        assertEquals("event-1", correlation.getValue().getId());
        return processor.getValue();
    }

    @Test
    void publishesAPersistentEventKeyedByEventIdWithoutWaiting() {
        confirmLater(true, false);
        ListingEvent event = event(ListingEvent.Type.SOLD);

        assertDoesNotThrow(() -> publisher.publish(event));

        Message message = new Message(new byte[0], new MessageProperties());
        captureProcessor(event).postProcessMessage(message);
        assertEquals("event-1", message.getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                message.getMessageProperties().getDeliveryMode());
    }

    @Test
    void aBrokerRejectionDoesNotReachTheCaller() {
        confirmLater(false, false);

        assertDoesNotThrow(() -> publisher.publish(event(ListingEvent.Type.SOLD)));
    }

    @Test
    void anUnroutableEventDoesNotReachTheCaller() {
        // Confirmed by the broker but no queue was bound: the return says so.
        confirmLater(true, true);

        assertDoesNotThrow(() -> publisher.publish(event(ListingEvent.Type.SOLD)));
    }

    @Test
    void aDeadConnectionDoesNotReachTheCaller() {
        doThrow(new org.springframework.amqp.AmqpConnectException(
                new java.net.ConnectException("refused")))
                .when(template).convertAndSend(
                        anyString(), anyString(), any(), any(MessagePostProcessor.class),
                        any(CorrelationData.class));

        assertDoesNotThrow(() -> publisher.publish(event(ListingEvent.Type.SOLD)));
    }

    @Test
    void eachTypeUsesItsOwnRoutingKey() {
        confirmLater(true, false);

        publisher.publish(event(ListingEvent.Type.SOLD));
        publisher.publish(event(ListingEvent.Type.DELISTED));
        publisher.publish(event(ListingEvent.Type.UPDATED));

        verify(template).convertAndSend(eq(RabbitTopology.EVENT_EXCHANGE),
                eq(RabbitTopology.SOLD_KEY), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        verify(template).convertAndSend(eq(RabbitTopology.EVENT_EXCHANGE),
                eq(RabbitTopology.DELISTED_KEY), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        verify(template).convertAndSend(eq(RabbitTopology.EVENT_EXCHANGE),
                eq(RabbitTopology.UPDATED_KEY), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }
}
