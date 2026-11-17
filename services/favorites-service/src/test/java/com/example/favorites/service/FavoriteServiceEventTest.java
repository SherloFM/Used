package com.example.favorites.service;

import com.example.favorites.client.AccountsClient;
import com.example.favorites.client.EmailClient;
import com.example.favorites.client.MarketplaceClient;
import com.example.favorites.client.NotificationClient;
import com.example.favorites.client.UserSummary;
import com.example.favorites.messaging.ListingEvent;
import com.example.favorites.model.Favorite;
import com.example.favorites.repository.FavoriteRepository;
import com.example.favorites.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The event-driven replacement for the HTTP callbacks. */
class FavoriteServiceEventTest {

    private FavoriteRepository favorites;
    private MarketplaceClient marketplace;
    private AccountsClient accounts;
    private EmailClient emails;
    private NotificationClient notifications;
    private FavoriteService service;

    @BeforeEach
    void setup() {
        favorites = mock(FavoriteRepository.class);
        marketplace = mock(MarketplaceClient.class);
        accounts = mock(AccountsClient.class);
        emails = mock(EmailClient.class);
        notifications = mock(NotificationClient.class);
        service = new FavoriteService(favorites, marketplace, accounts,
                mock(CurrentActorProvider.class), emails, notifications);
    }

    private Favorite favorite(long userId) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setListingId(7L);
        return favorite;
    }

    private ListingEvent event(ListingEvent.Type type, List<String> changedFields) {
        return new ListingEvent("e1", type, 7L, 3L, "Bike", 150.0, "SOLD", "/img/7",
                changedFields, type == ListingEvent.Type.SOLD ? "sold" : "delisted",
                ListingEvent.Source.PAYMENT,
                Instant.parse("2026-11-15T10:00:00Z"),
                Instant.parse("2026-11-15T10:15:30Z"));
    }

    @Test
    void anUpdatePushesEverySaverExceptTheSeller() {
        when(favorites.findByListingId(7L))
                .thenReturn(List.of(favorite(12L), favorite(13L), favorite(3L)));

        service.applyListingUpdated(event(ListingEvent.Type.UPDATED, List.of("price")));

        verify(notifications).sendToUser(eq(12L), eq("LISTING_UPDATED"), any(Map.class));
        verify(notifications).sendToUser(eq(13L), eq("LISTING_UPDATED"), any(Map.class));
        verify(notifications, never()).sendToUser(eq(3L), anyString(), any(Map.class));

        // The fix for problem 3: the title came from the event, not a callback.
        verifyNoInteractions(marketplace);
    }

    @Test
    void anUpdateWithoutChangedFieldsDoesNothing() {
        service.applyListingUpdated(event(ListingEvent.Type.UPDATED, List.of()));

        verifyNoInteractions(notifications, favorites);
    }

    @Test
    void aSalePushesDeletesAndEmailsTheSaversButNotTheSeller() {
        List<Favorite> saved = List.of(favorite(12L), favorite(13L), favorite(3L));
        when(favorites.findByListingId(7L)).thenReturn(saved);
        when(accounts.find(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "alice", "alice@example.com", "USER")));
        when(accounts.find(13L)).thenReturn(Optional.of(
                new UserSummary(13L, "bob", null, "USER")));

        service.applyListingRemoved(event(ListingEvent.Type.SOLD, List.of()));

        verify(notifications, times(2)).sendToUser(anyLong(), eq("LISTING_UNAVAILABLE"), any(Map.class));
        verify(notifications, never()).sendToUser(eq(3L), anyString(), any(Map.class));
        verify(favorites).deleteAll(saved);
        verify(emails).sendListingUnavailableEmail("alice@example.com", "Bike", "sold");
        // No email for the saver without an address.
        verify(emails, times(1)).sendListingUnavailableEmail(anyString(), anyString(), anyString());
        verifyNoInteractions(marketplace);
    }

    @Test
    void aDelistSaysNoLongerAvailableRatherThanSold() {
        when(favorites.findByListingId(7L)).thenReturn(List.of(favorite(12L)));
        when(accounts.find(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "alice", "alice@example.com", "USER")));

        service.applyListingRemoved(event(ListingEvent.Type.DELISTED, List.of()));

        verify(emails).sendListingUnavailableEmail("alice@example.com", "Bike", "delisted");
    }

    @Test
    void aRedeliveredRemovalRepeatsNothing() {
        // The previous delivery already deleted the favorites.
        when(favorites.findByListingId(7L)).thenReturn(List.of());

        service.applyListingRemoved(event(ListingEvent.Type.SOLD, List.of()));

        verifyNoInteractions(notifications, emails, accounts);
        verify(favorites, never()).deleteAll(any());
    }

    @Test
    void aMissingEmailLookupDoesNotStopTheOtherSavers() {
        when(favorites.findByListingId(7L))
                .thenReturn(List.of(favorite(12L), favorite(13L)));
        when(accounts.find(12L)).thenThrow(new IllegalStateException("Accounts is down"));
        when(accounts.find(13L)).thenReturn(Optional.of(
                new UserSummary(13L, "bob", "bob@example.com", "USER")));

        service.applyListingRemoved(event(ListingEvent.Type.SOLD, List.of()));

        verify(emails).sendListingUnavailableEmail("bob@example.com", "Bike", "sold");
    }
}
