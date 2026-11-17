package com.example.notification.messaging;

import com.example.notification.client.AccountsClient;
import com.example.notification.client.UserSummary;
import com.example.notification.service.EmailServices;
import com.example.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Sends what Marketplace used to send itself, but from a durable event and only
 * after the sale or delist committed.
 *
 * <p>Duplicate deliveries are stopped by {@link ProcessedEventGuard}: the event
 * is claimed in its own transaction before any message or email is sent.</p>
 */
@Component
public class ListingEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ListingEventsListener.class);

    private final NotificationService notificationService;
    private final EmailServices emailServices;
    private final AccountsClient accountsClient;
    private final ProcessedEventGuard guard;

    public ListingEventsListener(NotificationService notificationService,
                                 EmailServices emailServices,
                                 AccountsClient accountsClient,
                                 ProcessedEventGuard guard) {
        this.notificationService = notificationService;
        this.emailServices = emailServices;
        this.accountsClient = accountsClient;
        this.guard = guard;
    }

    @RabbitListener(queues = RabbitTopology.LISTING_EVENTS_QUEUE)
    public void onListingEvent(ListingEvent event) {

        if (!guard.claim(event.eventId(), event.type().name(), event.listingId())) {
            log.info("Skipping event {} ({}): already handled", event.eventId(), event.type());
            return;
        }

        try {
            switch (event.type()) {
                case SOLD -> onSold(event);
                case DELISTED -> onDelisted(event);
                default -> log.debug("Nothing to notify for {}", event.type());
            }
        } catch (RuntimeException exception) {
            // Give the claim back so the retry is not mistaken for a duplicate.
            guard.release(event.eventId());
            throw exception;
        }
    }

    private void onSold(ListingEvent event) {

        // The in-app push covers both sale paths, including COD, which used to
        // push from inside the payment transaction - before it had committed.
        notificationService.sendToUser(event.sellerId(), "LISTING_SOLD", Map.of(
                "title", "Item sold",
                "message", "Your item '" + event.title() + "' was sold!",
                "type", "success",
                "listingId", event.listingId()));

        emailOf(event.sellerId()).ifPresent(email ->
                emailServices.sendListingSoldEmail(email, event.title()));
    }

    private void onDelisted(ListingEvent event) {

        // The owner cancelled it themselves: no seller email, same rule as before.
        if (event.source() != ListingEvent.Source.ADMIN
                && event.source() != ListingEvent.Source.MODERATION) {
            return;
        }

        emailOf(event.sellerId()).ifPresent(email ->
                emailServices.sendListingDeletedEmail(email, event.title()));
    }

    /**
     * A missing or unreachable account is not worth a retry storm: the event is
     * acknowledged and the seller simply has no email on file.
     */
    private Optional<String> emailOf(Long userId) {

        if (userId == null) {
            return Optional.empty();
        }

        try {
            return accountsClient.find(userId)
                    .map(UserSummary::email)
                    .filter(email -> email != null && !email.isBlank());
        } catch (RuntimeException exception) {
            log.warn("Could not look up user {}; no seller email sent", userId, exception);
            return Optional.empty();
        }
    }
}
