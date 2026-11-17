package com.example.moderation.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ListingCommandPublisherTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final ListingCommandPublisher publisher = new ListingCommandPublisher(template);

    private ListingDelistRequested command() {
        return new ListingDelistRequested(
                5L, 7L, 99L, "admin", "Removed by moderation (report #5)");
    }

    /** Completes the confirm future the way the broker would. */
    private void confirm(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(
                        new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", "listing.commands", "listing.delist"));
            }
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(ack, ack ? null : "broker refused"));
            return null;
        }).when(template).convertAndSend(
                any(String.class), any(String.class), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void publishesAPersistentCommandKeyedByReportId() {
        confirm(true, false);

        assertTrue(publisher.requestDelist(command()));

        ArgumentCaptor<MessagePostProcessor> processor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation =
                ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(
                eq(RabbitTopology.COMMAND_EXCHANGE),
                eq(RabbitTopology.DELIST_ROUTING_KEY),
                eq(command()),
                processor.capture(),
                correlation.capture());
        assertEquals("delist-5", correlation.getValue().getId());

        Message message = new Message(new byte[0], new MessageProperties());
        processor.getValue().postProcessMessage(message);
        assertEquals("5", message.getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                message.getMessageProperties().getDeliveryMode());
    }

    @Test
    void aNackIsReportedAsFailure() {
        confirm(false, false);

        assertFalse(publisher.requestDelist(command()));
    }

    @Test
    void anUnroutableMessageIsReportedAsFailureEvenThoughItWasConfirmed() {
        // The broker confirms unroutable messages; only the return says so.
        confirm(true, true);

        assertFalse(publisher.requestDelist(command()));
    }
}
