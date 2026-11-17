package com.example.marketplace.messaging;

import com.example.marketplace.model.Listings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The one API every listing change goes through. */
class ListingLifecycleTest {

    private ApplicationEventPublisher publisher;
    private ListingLifecycle lifecycle;

    @BeforeEach
    void setup() {
        publisher = mock(ApplicationEventPublisher.class);
        lifecycle = new ListingLifecycle(publisher);
        // The guard refuses to publish without an active transaction.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        // One test clears it itself to prove the guard; never clear twice.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Listings listing() {
        Listings listing = new Listings();
        listing.setId(7L);
        listing.setUserId(3L);
        listing.setTitle("Bike");
        listing.setPrice(150.0);
        listing.setStatus(Listings.Status.ACTIVE);
        listing.setImgUrl("/img/7");
        listing.setUpdatedAt(LocalDateTime.of(2026, 11, 15, 10, 0));
        return listing;
    }

    private ListingEvent captured() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        return (ListingEvent) event.getValue();
    }

    @Test
    void updatedCarriesTheChangedFieldsAndRowTimestamp() {
        ListingEvent event = null;
        lifecycle.updated(listing(), List.of("title", "price"));
        event = captured();

        assertEquals(ListingEvent.Type.UPDATED, event.type());
        assertEquals(ListingEvent.Source.OWNER, event.source());
        assertEquals("ACTIVE", event.status());
        assertEquals(List.of("title", "price"), event.changedFields());
        assertNull(event.reason());
        assertEquals(7L, event.listingId());
        assertEquals(3L, event.sellerId());
        assertEquals("Bike", event.title());
        assertEquals("/img/7", event.imgUrl());
        assertEquals(
                LocalDateTime.of(2026, 11, 15, 10, 0)
                        .atZone(ZoneId.systemDefault()).toInstant(),
                event.updatedAt());
        assertNotNull(event.occurredAt());
        assertNotNull(event.eventId());
    }

    @Test
    void anUnchangedListingPublishesNothing() {
        lifecycle.updated(listing(), List.of());

        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void soldForcesTheSoldStatusInsteadOfTrustingAStaleEntity() {
        Listings stale = listing();   // still ACTIVE

        lifecycle.sold(stale, ListingEvent.Source.COD);

        ListingEvent event = captured();
        assertEquals(ListingEvent.Type.SOLD, event.type());
        assertEquals("SOLD", event.status());
        assertEquals("sold", event.reason());
        assertEquals(ListingEvent.Source.COD, event.source());
    }

    @Test
    void delistedKeepsTheReasonAndTheSource() {
        lifecycle.delisted(listing(), ListingEvent.Source.MODERATION,
                "Removed by moderation (report #5)");

        ListingEvent event = captured();
        assertEquals(ListingEvent.Type.DELISTED, event.type());
        assertEquals("DELISTED", event.status());
        assertEquals("Removed by moderation (report #5)", event.reason());
        assertEquals(ListingEvent.Source.MODERATION, event.source());
    }

    @Test
    void aCallOutsideATransactionIsRefusedInsteadOfBeingLost() {
        TransactionSynchronizationManager.clearSynchronization();

        lifecycle.sold(listing(), ListingEvent.Source.PAYMENT);

        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void aNullRowTimestampIsTolerated() {
        Listings listing = listing();
        listing.setUpdatedAt(null);

        lifecycle.delisted(listing, ListingEvent.Source.OWNER, "Removed by you");

        assertNull(captured().updatedAt());
    }
}
