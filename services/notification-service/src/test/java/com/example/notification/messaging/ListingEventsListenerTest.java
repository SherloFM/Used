package com.example.notification.messaging;

import com.example.notification.client.AccountsClient;
import com.example.notification.client.UserSummary;
import com.example.notification.service.EmailServices;
import com.example.notification.service.NotificationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListingEventsListenerTest {

    private NotificationService notifications;
    private EmailServices emails;
    private AccountsClient accounts;
    private ProcessedEventGuard guard;
    private ListingEventsListener listener;

    @BeforeEach
    void setup() {
        notifications = mock(NotificationService.class);
        emails = mock(EmailServices.class);
        accounts = mock(AccountsClient.class);
        guard = mock(ProcessedEventGuard.class);
        listener = new ListingEventsListener(notifications, emails, accounts, guard);
        when(guard.claim(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    private ListingEvent event(ListingEvent.Type type, ListingEvent.Source source) {
        return new ListingEvent("e1", type, 7L, 3L, "Bike", 150.0, "SOLD", "/img/7",
                List.of(), "sold", source,
                Instant.parse("2026-11-15T10:00:00Z"),
                Instant.parse("2026-11-15T10:15:30Z"));
    }

    @Test
    void aSalePushesAndEmailsTheSeller() {
        when(accounts.find(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "bob", "bob@example.com", "USER")));

        listener.onListingEvent(event(ListingEvent.Type.SOLD, ListingEvent.Source.PAYMENT));

        verify(notifications).sendToUser(eq(3L), eq("LISTING_SOLD"), any(Map.class));
        verify(emails).sendListingSoldEmail("bob@example.com", "Bike");
    }

    @Test
    void anAdminDelistEmailsTheSeller() {
        when(accounts.find(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "bob", "bob@example.com", "USER")));

        listener.onListingEvent(event(ListingEvent.Type.DELISTED, ListingEvent.Source.ADMIN));

        verify(emails).sendListingDeletedEmail("bob@example.com", "Bike");
    }

    @Test
    void aModerationDelistEmailsTheSeller() {
        when(accounts.find(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "bob", "bob@example.com", "USER")));

        listener.onListingEvent(event(ListingEvent.Type.DELISTED,
                ListingEvent.Source.MODERATION));

        verify(emails).sendListingDeletedEmail("bob@example.com", "Bike");
    }

    @Test
    void anOwnerCancellationSendsNothing() {
        listener.onListingEvent(event(ListingEvent.Type.DELISTED, ListingEvent.Source.OWNER));

        verifyNoInteractions(emails, notifications);
    }

    @Test
    void anUnknownSellerIsAcknowledgedWithoutAnEmail() {
        when(accounts.find(3L)).thenReturn(Optional.empty());

        listener.onListingEvent(event(ListingEvent.Type.SOLD, ListingEvent.Source.PAYMENT));

        // The in-app push still happened; only the email is skipped.
        verify(notifications).sendToUser(eq(3L), eq("LISTING_SOLD"), any(Map.class));
        verify(emails, never()).sendListingSoldEmail(anyString(), anyString());
    }

    @Test
    void anUnreachableAccountsServiceDoesNotFailTheMessage() {
        when(accounts.find(3L)).thenThrow(new IllegalStateException("Accounts is down"));

        listener.onListingEvent(event(ListingEvent.Type.SOLD, ListingEvent.Source.COD));

        verify(emails, never()).sendListingSoldEmail(anyString(), anyString());
    }

    @Test
    void aDuplicateDeliveryIsSkippedBeforeAnythingIsSent() {
        when(guard.claim("e1", "SOLD", 7L)).thenReturn(false);

        listener.onListingEvent(event(ListingEvent.Type.SOLD, ListingEvent.Source.PAYMENT));

        verifyNoInteractions(notifications, emails, accounts);
    }

    @Test
    void aFailureReleasesTheClaimSoTheRetryCanRun() {
        when(accounts.find(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "bob", "bob@example.com", "USER")));
        org.mockito.Mockito.doThrow(new IllegalStateException("mail server down"))
                .when(emails).sendListingSoldEmail(anyString(), anyString());

        try {
            listener.onListingEvent(event(ListingEvent.Type.SOLD, ListingEvent.Source.PAYMENT));
        } catch (IllegalStateException expected) {
            // Rethrown so the listener retries, then dead-letters.
        }

        verify(guard).release("e1");
    }
}
