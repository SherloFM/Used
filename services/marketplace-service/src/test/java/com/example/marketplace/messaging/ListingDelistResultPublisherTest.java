package com.example.marketplace.messaging;

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

class ListingDelistResultPublisherTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final ListingDelistResultPublisher publisher =
            new ListingDelistResultPublisher(template);

    private ListingDelistResult result() {
        return new ListingDelistResult(5L, DelistOutcome.DELISTED, "Listing delisted.");
    }

    private void confirm(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(
                        new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", "listing.events", "listing.delist.result"));
            }
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(ack, ack ? null : "broker refused"));
            return null;
        }).when(template).convertAndSend(
                any(String.class), any(String.class), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void publishesAPersistentResultKeyedByReportId() {
        confirm(true, false);

        assertTrue(publisher.publish(result()));

        ArgumentCaptor<MessagePostProcessor> processor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation =
                ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(
                eq(RabbitTopology.EVENT_EXCHANGE),
                eq(RabbitTopology.RESULT_ROUTING_KEY),
                eq(result()),
                processor.capture(),
                correlation.capture());
        assertEquals("delist-result-5", correlation.getValue().getId());

        Message message = new Message(new byte[0], new MessageProperties());
        processor.getValue().postProcessMessage(message);
        assertEquals("5", message.getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                message.getMessageProperties().getDeliveryMode());
    }

    @Test
    void aNackIsReportedAsFailure() {
        confirm(false, false);

        assertFalse(publisher.publish(result()));
    }

    @Test
    void anUnroutableResultIsReportedAsFailureEvenThoughItWasConfirmed() {
        confirm(true, true);

        assertFalse(publisher.publish(result()));
    }
}
