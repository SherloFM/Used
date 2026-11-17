package com.example.favorites.messaging;

import com.example.favorites.service.FavoriteService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ListingEventsListenerTest {

    private final FavoriteService favoriteService = mock(FavoriteService.class);
    private final ListingEventsListener listener = new ListingEventsListener(favoriteService);

    private ListingEvent event(ListingEvent.Type type) {
        return new ListingEvent("e1", type, 7L, 3L, "Bike", 150.0, "ACTIVE", null,
                List.of("price"), "sold", ListingEvent.Source.PAYMENT,
                Instant.parse("2026-11-15T10:00:00Z"),
                Instant.parse("2026-11-15T10:15:30Z"));
    }

    @Test
    void anUpdateGoesToTheUpdatePath() {
        listener.onListingEvent(event(ListingEvent.Type.UPDATED));

        verify(favoriteService).applyListingUpdated(event(ListingEvent.Type.UPDATED));
        verifyNoMoreInteractions(favoriteService);
    }

    @Test
    void salesAndDelistsGoToTheRemovalPath() {
        listener.onListingEvent(event(ListingEvent.Type.SOLD));
        listener.onListingEvent(event(ListingEvent.Type.DELISTED));

        verify(favoriteService).applyListingRemoved(event(ListingEvent.Type.SOLD));
        verify(favoriteService).applyListingRemoved(event(ListingEvent.Type.DELISTED));
    }

    @Test
    void aCreationIsIgnored() {
        listener.onListingEvent(event(ListingEvent.Type.CREATED));

        verifyNoMoreInteractions(favoriteService);
    }
}
