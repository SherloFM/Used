package com.example.marketplace.messaging;

import com.example.marketplace.service.ListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingDelistListenerTest {

    private ListingService listings;
    private ListingDelistResultPublisher results;
    private ListingDelistListener listener;

    @BeforeEach
    void setup() {
        listings = mock(ListingService.class);
        results = mock(ListingDelistResultPublisher.class);
        listener = new ListingDelistListener(listings, results);
    }

    private ListingDelistRequested command() {
        return new ListingDelistRequested(5L, 7L, 99L, "admin",
                "Removed by moderation (report #5)");
    }

    @Test
    void reportsTheServiceOutcomeBackToModeration() {
        when(listings.adminDeleteListingAs(7L,
                "Removed by moderation (report #5)", 99L, "admin"))
                .thenReturn(DelistOutcome.ALREADY_DELISTED);
        when(results.publish(any())).thenReturn(true);

        listener.onDelistRequested(command());

        ArgumentCaptor<ListingDelistResult> captured =
                ArgumentCaptor.forClass(ListingDelistResult.class);
        verify(results).publish(captured.capture());
        assertEquals(5L, captured.getValue().reportId());
        assertEquals(DelistOutcome.ALREADY_DELISTED, captured.getValue().outcome());
    }

    @Test
    void unexpectedFailureIsReportedAsFailedSoTheSweepRetries() {
        when(listings.adminDeleteListingAs(7L,
                "Removed by moderation (report #5)", 99L, "admin"))
                .thenThrow(new IllegalStateException("database is down"));
        when(results.publish(any())).thenReturn(true);

        listener.onDelistRequested(command());

        ArgumentCaptor<ListingDelistResult> captured =
                ArgumentCaptor.forClass(ListingDelistResult.class);
        verify(results).publish(captured.capture());
        assertEquals(DelistOutcome.FAILED, captured.getValue().outcome());
        assertEquals("database is down", captured.getValue().message());
    }

    @Test
    void anUnconfirmedResultIsRejectedSoTheCommandIsRedelivered() {
        when(listings.adminDeleteListingAs(7L,
                "Removed by moderation (report #5)", 99L, "admin"))
                .thenReturn(DelistOutcome.DELISTED);
        when(results.publish(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> listener.onDelistRequested(command()));
    }
}
