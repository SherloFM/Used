package com.example.marketplace.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingContractsTest {

    private final MessageConverter converter = new JacksonJsonMessageConverter();

    @Test
    void delistRequestRoundTripsThroughTheConsumerType() {
        ListingDelistRequested command = new ListingDelistRequested(
                5L, 7L, 99L, "admin", "Removed by moderation (report #5)");

        Message message = converter.toMessage(command, new MessageProperties());

        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
        // Listeners set the inferred argument type from the method signature. It wins
        // over the publisher's __TypeId__, so both services can use their own packages.
        message.getMessageProperties().setInferredArgumentType(ListingDelistRequested.class);
        assertEquals(command, converter.fromMessage(message));
    }

    @Test
    void delistResultRoundTripsAndOnlySuccessfulOutcomesSucceed() {
        ListingDelistResult result = new ListingDelistResult(
                5L, DelistOutcome.ALREADY_DELISTED, "already gone");
        Message message = converter.toMessage(result, new MessageProperties());
        message.getMessageProperties().setInferredArgumentType(ListingDelistResult.class);

        assertEquals(result, converter.fromMessage(message));
        assertTrue(result.succeeded());
        assertTrue(new ListingDelistResult(5L, DelistOutcome.DELISTED, "done").succeeded());
        assertFalse(new ListingDelistResult(5L, DelistOutcome.NOT_FOUND, "missing").succeeded());
        assertFalse(new ListingDelistResult(5L, DelistOutcome.SOLD, "sold").succeeded());
        assertFalse(new ListingDelistResult(5L, DelistOutcome.FAILED, "boom").succeeded());
    }

    @Test
    void invalidContractsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDelistRequested(null, 7L, 99L, "admin", "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDelistRequested(5L, 0L, 99L, "admin", "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDelistRequested(5L, 7L, 99L, "  ", "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDelistRequested(5L, 7L, 99L, "admin", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDelistResult(5L, null, "missing outcome"));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDelistResult(0L, DelistOutcome.DELISTED, "bad id"));
    }
}
