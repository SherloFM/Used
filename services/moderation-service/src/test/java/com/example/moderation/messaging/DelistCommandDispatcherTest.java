package com.example.moderation.messaging;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelistCommandDispatcherTest {

    private final ListingCommandPublisher publisher = mock(ListingCommandPublisher.class);
    private final DelistCommandDispatcher dispatcher = new DelistCommandDispatcher(publisher);

    private ListingDelistRequested command() {
        return new ListingDelistRequested(
                5L, 7L, 99L, "admin", "Removed by moderation (report #5)");
    }

    @Test
    void sendsTheCommittedCommand() {
        when(publisher.requestDelist(command())).thenReturn(true);

        dispatcher.onDelistRequested(command());

        verify(publisher).requestDelist(command());
    }

    @Test
    void anUnconfirmedPublishDoesNotReachTheAdmin() {
        when(publisher.requestDelist(command())).thenReturn(false);

        // The report is already committed as DELISTING; the sweep retries.
        dispatcher.onDelistRequested(command());

        verify(publisher).requestDelist(command());
    }
}
