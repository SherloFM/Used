package com.example.favorites.service;

import com.example.favorites.client.EmailClient;
import com.example.favorites.client.AccountsClient;
import com.example.favorites.client.NotificationClient;

import com.example.favorites.exceptions.InformationExistException;
import com.example.favorites.exceptions.ResourceNotFoundException;
import com.example.favorites.model.Favorite;
import com.example.favorites.client.ListingSummary;
import com.example.favorites.client.MarketplaceClient;
import com.example.favorites.messaging.ListingEvent;
import com.example.favorites.model.responses.FavoriteResponse;
import com.example.favorites.security.CurrentActorProvider;
import com.example.favorites.repository.FavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final MarketplaceClient marketplaceClient;
    private final AccountsClient accountsClient;
    private final CurrentActorProvider currentActorProvider;
    private final EmailClient emailClient;
    private final NotificationClient notificationClient;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           MarketplaceClient marketplaceClient,
                           AccountsClient accountsClient,
                           CurrentActorProvider currentActorProvider,
                           EmailClient emailClient,
                           NotificationClient notificationClient) {
        this.favoriteRepository = favoriteRepository;
        this.marketplaceClient = marketplaceClient;
        this.accountsClient = accountsClient;
        this.currentActorProvider = currentActorProvider;
        this.emailClient = emailClient;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public FavoriteResponse addFavorite(Long listingId) {
        Long userId = currentActorProvider.get().id();

        // HTTP returns a snapshot, not a lock. A concurrent sale may leave a stale
        // favorite until downstream cleanup/reconciliation is implemented.
        ListingSummary listing = marketplaceClient.find(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (userId.equals(listing.sellerId())) {
            throw new InformationExistException("You cannot favorite your own listing");
        }

        if (!"ACTIVE".equals(listing.status())) {
            throw new InformationExistException("Only active listings can be saved");
        }

        if (favoriteRepository.existsByUserIdAndListingId(userId, listingId)) {
            throw new InformationExistException("Listing is already in your favorites");
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setListingId(listingId);
        Favorite saved = favoriteRepository.save(favorite);

        return FavoriteResponse.from(saved, listing);
    }

    @Transactional
    public void removeFavorite(Long listingId) {
        Long userId = currentActorProvider.get().id();

        if (!favoriteRepository.existsByUserIdAndListingId(userId, listingId)) {
            throw new ResourceNotFoundException("Listing is not in your favorites");
        }
        favoriteRepository.deleteByUserIdAndListingId(userId, listingId);
    }

    @Transactional(readOnly = true)
    public Page<FavoriteResponse> getMyFavorites(int page, int size) {
        Long userId = currentActorProvider.get().id();
        return favoriteRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(favorite -> FavoriteResponse.from(
                        favorite, marketplaceClient.find(favorite.getListingId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long listingId) {
        Long userId = currentActorProvider.get().id();
        return favoriteRepository.existsByUserIdAndListingId(userId, listingId);
    }

    @Transactional(readOnly = true)
    public long countFavorites(Long listingId) {
        return favoriteRepository.countByListingId(listingId);
    }

    /* ------------------------------------------------------------------
     * Event-driven paths (8C). These replaced the two HTTP callbacks that
     * 8D removed, along with their /api/internal/favorites endpoints.
     * ------------------------------------------------------------------ */

    /**
     * A saved listing changed. The event already carries the title, so nothing
     * calls Marketplace back: an update no longer depends on Marketplace being
     * up when this runs.
     */
    @Transactional
    public void applyListingUpdated(ListingEvent event) {

        if (event.changedFields().isEmpty()) {
            return;
        }

        String message = "The listing '" + event.title() + "' you saved was updated: "
                + String.join(", ", event.changedFields()) + ".";

        for (Favorite favorite : favoriteRepository.findByListingId(event.listingId())) {
            if (favorite.getUserId().equals(event.sellerId())) {
                continue;
            }
            notificationClient.sendToUser(favorite.getUserId(), "LISTING_UPDATED", Map.of(
                    "title", "A saved listing was updated",
                    "message", message,
                    "type", "info",
                    "listingId", event.listingId()));
        }
    }

    /**
     * A saved listing was sold or delisted. The favorites are deleted in this
     * transaction, BEFORE the emails go out, so a redelivered event finds
     * nothing and repeats neither the pushes nor the emails.
     */
    @Transactional
    public void applyListingRemoved(ListingEvent event) {

        List<Favorite> favorites = favoriteRepository.findByListingId(event.listingId());
        if (favorites.isEmpty()) {
            return;
        }

        boolean sold = event.type() == ListingEvent.Type.SOLD;
        String reason = sold ? "sold" : "delisted";
        String title = (event.title() == null || event.title().isBlank())
                ? "a listing"
                : event.title();
        String message = "The listing '" + title + "' you saved "
                + (sold ? "was sold." : "is no longer available.");

        List<Favorite> recipients = favorites.stream()
                .filter(favorite -> !favorite.getUserId().equals(event.sellerId()))
                .toList();

        for (Favorite favorite : recipients) {
            notificationClient.sendToUser(favorite.getUserId(), "LISTING_UNAVAILABLE", Map.of(
                    "title", "A saved listing is no longer available",
                    "message", message,
                    "type", "info",
                    "listingId", event.listingId()));
        }

        favoriteRepository.deleteAll(favorites);

        // Emails after commit: a rollback must not have sent them.
        afterCommit(() -> recipients.forEach(favorite -> {
            try {
                accountsClient.find(favorite.getUserId()).ifPresent(user -> {
                    if (user.email() != null) {
                        emailClient.sendListingUnavailableEmail(user.email(), title, reason);
                    }
                });
            } catch (RuntimeException exception) {
                log.error("Failed to email user {} about unavailable listing {}",
                        favorite.getUserId(), event.listingId(), exception);
            }
        }));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
