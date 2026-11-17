package com.example.marketplace.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.MethodParameter;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ListingEventDispatcherTest {

    /** Stands in for another service's copy of the contract. */
    private record ConsumerCopy(String eventId, String type, Long listingId, Long sellerId,
                                String title, Double price, String status, String imgUrl,
                                List<String> changedFields, String reason, String source,
                                Instant updatedAt, Instant occurredAt) {
    }

    public static final class ConsumerListener {
        public void onListingEvent(ConsumerCopy event) {
        }
    }

    @Test
    void delegatesToThePublisher() {
        ListingEventPublisher publisher = mock(ListingEventPublisher.class);
        ListingEventDispatcher dispatcher = new ListingEventDispatcher(publisher);

        ListingEvent event = event();

        dispatcher.onListingEvent(event);

        verify(publisher).publish(event);
    }

    @Test
    void runsOnlyAfterCommitAndNeverFallsBack() throws NoSuchMethodException {
        Method method = ListingEventDispatcher.class
                .getMethod("onListingEvent", ListingEvent.class);
        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
        assertFalse(annotation.fallbackExecution());
    }

    /**
     * The cross-service contract: the wire carries a logical type id, and a
     * consumer with its OWN copy of the record resolves it. A Java class name
     * from another package is never trusted or loaded.
     */
    @Test
    void aConsumerWithItsOwnCopyResolvesTheLogicalTypeId() throws NoSuchMethodException {

        Message message = producer().toMessage(event(), new MessageProperties());

        assertEquals(RabbitTopology.TYPE_LISTING_EVENT,
                message.getMessageProperties().getHeader("__TypeId__"));

        Object parsed = consumer().fromMessage(message, hint());

        assertEquals(ConsumerCopy.class, parsed.getClass());
        ConsumerCopy copy = (ConsumerCopy) parsed;
        assertEquals("e1", copy.eventId());
        assertEquals("SOLD", copy.type());
        assertEquals("Bike", copy.title());
        assertEquals(Instant.parse("2026-11-15T10:15:30Z"), copy.occurredAt());
        assertEquals(Instant.parse("2026-11-15T10:00:00Z"), copy.updatedAt());
    }

    private ListingEvent event() {
        return new ListingEvent("e1", ListingEvent.Type.SOLD, 7L, 3L, "Bike", 150.0,
                "SOLD", "/img/7", List.of("title"), "sold", ListingEvent.Source.PAYMENT,
                Instant.parse("2026-11-15T10:00:00Z"),
                Instant.parse("2026-11-15T10:15:30Z"));
    }

    private SmartMessageConverter producer() {
        return (SmartMessageConverter) new RabbitConfiguration().jsonMessageConverter();
    }

    private SmartMessageConverter consumer() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        DefaultJacksonJavaTypeMapper mapper = new DefaultJacksonJavaTypeMapper();
        mapper.setIdClassMapping(Map.of(RabbitTopology.TYPE_LISTING_EVENT, ConsumerCopy.class));
        converter.setJavaTypeMapper(mapper);
        return converter;
    }

    private MethodParameter hint() throws NoSuchMethodException {
        Method method = ConsumerListener.class.getMethod("onListingEvent", ConsumerCopy.class);
        return new MethodParameter(method, 0);
    }
}
