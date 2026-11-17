package com.example.marketplace.controller;

import com.example.marketplace.messaging.DelistOutcome;
import com.example.marketplace.model.requests.InternalDelistRequest;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.service.ListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalListingControllerTest {
    private ListingService listings;
    private InternalListingController controller;

    @BeforeEach
    void setup() {
        listings = mock(ListingService.class);
        controller = new InternalListingController(mock(ListingRepository.class), listings);
    }

    @Test
    void delistDelegatesTheTrustedAdminIdentity() {
        when(listings.adminDeleteListingAs(7L, "policy violation", 99L, "admin"))
                .thenReturn(DelistOutcome.DELISTED);

        var response = controller.delist(7L, new InternalDelistRequest("policy violation", 99L, "admin"));

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(listings).adminDeleteListingAs(7L, "policy violation", 99L, "admin");
    }

    @Test
    void missingListingBecomesNotFound() {
        when(listings.adminDeleteListingAs(7L, "policy violation", 99L, "admin"))
                .thenReturn(DelistOutcome.NOT_FOUND);

        var response = controller.delist(7L, new InternalDelistRequest("policy violation", 99L, "admin"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Listing not found", response.getBody().get("message"));
    }

    @Test
    void soldListingBecomesAConflict() {
        when(listings.adminDeleteListingAs(7L, "policy violation", 99L, "admin"))
                .thenReturn(DelistOutcome.SOLD);

        var response = controller.delist(7L, new InternalDelistRequest("policy violation", 99L, "admin"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("A sold listing cannot be removed", response.getBody().get("message"));
    }

    @Test
    void alreadyDelistedIsSuccessBecauseRetriesMustBeSafe() {
        when(listings.adminDeleteListingAs(8L, "policy violation", 99L, "admin"))
                .thenReturn(DelistOutcome.ALREADY_DELISTED);

        var response = controller.delist(8L, new InternalDelistRequest("policy violation", 99L, "admin"));

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    void unexpectedFailureBecomesAServerError() {
        when(listings.adminDeleteListingAs(7L, "policy violation", 99L, "admin"))
                .thenReturn(DelistOutcome.FAILED);

        var response = controller.delist(7L, new InternalDelistRequest("policy violation", 99L, "admin"));

        assertEquals(500, response.getStatusCode().value());
    }
}
