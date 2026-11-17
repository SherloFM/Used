package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.AuditClient;
import com.example.marketplace.client.MediaClient;
import com.example.marketplace.client.UserSummary;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.messaging.DelistOutcome;
import com.example.marketplace.messaging.ListingEvent;
import com.example.marketplace.messaging.ListingLifecycle;
import com.example.marketplace.model.Listings;
import com.example.marketplace.repository.CategoryRepository;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.TransactionsRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The idempotent delist contract the RabbitMQ saga relies on. */
class ListingServiceDelistTest {

    private ListingRepository listings;
    private TransactionsRepository transactions;
    private CurrentActorProvider actors;
    private AccountsClient accounts;
    private AuditClient audit;
    private ListingLifecycle lifecycle;
    private ListingService service;

    @BeforeEach
    void setup() {
        listings = mock(ListingRepository.class);
        transactions = mock(TransactionsRepository.class);
        actors = mock(CurrentActorProvider.class);
        accounts = mock(AccountsClient.class);
        audit = mock(AuditClient.class);
        lifecycle = mock(ListingLifecycle.class);
        service = new ListingService(listings, transactions, actors, accounts,
                mock(MediaClient.class), audit,
                mock(CategoryRepository.class), lifecycle,
                new TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class)));
    }

    private Listings listing(Listings.Status status) {
        Listings listing = new Listings();
        listing.setId(10L);
        listing.setUserId(3L);
        listing.setTitle("Bike");
        listing.setStatus(status);
        return listing;
    }

    @Test
    void activeListingIsDelistedWithEverySideEffect() {
        Listings listing = listing(Listings.Status.ACTIVE);
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        when(accounts.find(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "bob", "bob@example.com", "USER")));

        DelistOutcome outcome =
                service.adminDeleteListingAs(10L, "policy violation", 99L, "admin");

        assertEquals(DelistOutcome.DELISTED, outcome);
        assertEquals(Listings.Status.DELISTED, listing.getStatus());
        assertEquals("policy violation", listing.getStatusReason());
        verify(listings).saveAndFlush(listing);
        // One event carries the favorites cleanup AND the seller email.
        verify(lifecycle).delisted(listing, ListingEvent.Source.MODERATION,
                "policy violation");
        verify(audit).log(eq("LISTING_DELISTED"), eq(99L), eq("admin"),
                contains("Bike"), eq(3L));
    }

    @Test
    void repeatingTheCommandRepeatsNoSideEffects() {
        Listings listing = listing(Listings.Status.DELISTED);
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));

        DelistOutcome outcome =
                service.adminDeleteListingAs(10L, "policy violation", 99L, "admin");

        assertEquals(DelistOutcome.ALREADY_DELISTED, outcome);
        verify(listings, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(lifecycle, audit);
    }

    @Test
    void soldListingIsRefusedWithoutSideEffects() {
        Listings listing = listing(Listings.Status.SOLD);
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));

        assertEquals(DelistOutcome.SOLD,
                service.adminDeleteListingAs(10L, "policy", 99L, "admin"));
        verify(listings, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(lifecycle, audit);
    }

    @Test
    void missingListingIsReportedNotThrown() {
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertEquals(DelistOutcome.NOT_FOUND,
                service.adminDeleteListingAs(10L, "policy", 99L, "admin"));
    }

    @Test
    void blankAdminIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.adminDeleteListingAs(10L, "policy", 99L, "  "));
        verifyNoInteractions(listings);
    }

    @Test
    void browserAdminEndpointStillGetsTheOldConflicts() {
        when(actors.get()).thenReturn(new CurrentActor(99L, "admin", "ADMIN"));
        Listings sold = listing(Listings.Status.SOLD);
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.of(sold));

        assertThrows(InformationExistException.class,
                () -> service.adminDeleteListing(10L, "policy"));

        Listings removed = listing(Listings.Status.DELISTED);
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.of(removed));
        assertThrows(InformationExistException.class,
                () -> service.adminDeleteListing(10L, "policy"));
    }

    @Test
    void theBrowserAdminPathTakesTheAdminSource() {
        when(actors.get()).thenReturn(new CurrentActor(99L, "admin", "ADMIN"));
        Listings listing = listing(Listings.Status.ACTIVE);
        when(listings.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));

        service.adminDeleteListing(10L, "policy violation");

        verify(lifecycle).delisted(listing, ListingEvent.Source.ADMIN, "policy violation");
    }
}
