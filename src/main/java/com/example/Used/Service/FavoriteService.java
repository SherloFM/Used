package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.Favorite;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Responses.FavoriteResponse;
import com.example.Used.Model.User;
import com.example.Used.Repository.FavoriteRepository;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.UserRepository;
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
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;
    private final NotificationService notificationService;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           ListingRepository listingRepository,
                           UserRepository userRepository,
                           CurrentUserService currentUserService,
                           EmailServices emailServices,
                           NotificationService notificationService) {
        this.favoriteRepository = favoriteRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
        this.notificationService = notificationService;
    }

    @Transactional
    public FavoriteResponse addFavorite(Long listingId) {
        User me = currentUserService.getCurrentUser();

        // Row lock on the listing. The sale/delist flows take the same lock
        // before they remove favorites, so this makes add-vs-sale atomic:
        // either we commit while the listing is still ACTIVE and sale
        // cleanup removes our favorite, or we wait and are rejected.
        Listings listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getUser() != null && listing.getUser().getId().equals(me.getId())) {
            throw new InformationExistException("You cannot favorite your own listing");
        }

        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException("Only active listings can be saved");
        }

        if (favoriteRepository.existsByUserIdAndListingId(me.getId(), listingId)) {
            throw new InformationExistException("Listing is already in your favorites");
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(me.getId());
        favorite.setListingId(listingId);
        Favorite saved = favoriteRepository.save(favorite);

        return FavoriteResponse.from(saved, listing);
    }

    @Transactional
    public void removeFavorite(Long listingId) {
        User me = currentUserService.getCurrentUser();

        if (!favoriteRepository.existsByUserIdAndListingId(me.getId(), listingId)) {
            throw new ResourceNotFoundException("Listing is not in your favorites");
        }
        favoriteRepository.deleteByUserIdAndListingId(me.getId(), listingId);
    }

    @Transactional(readOnly = true)
    public Page<FavoriteResponse> getMyFavorites(int page, int size) {
        User me = currentUserService.getCurrentUser();
        return favoriteRepository
                .findByUserIdOrderByCreatedAtDesc(me.getId(), PageRequest.of(page, size))
                .map(favorite -> FavoriteResponse.from(
                        favorite, listingRepository.findById(favorite.getListingId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long listingId) {
        User me = currentUserService.getCurrentUser();
        return favoriteRepository.existsByUserIdAndListingId(me.getId(), listingId);
    }

    @Transactional(readOnly = true)
    public long countFavorites(Long listingId) {
        return favoriteRepository.countByListingId(listingId);
    }

    @Transactional
    public void notifyListingUpdated(Listings listing, List<String> changedFields) {
        if (changedFields.isEmpty()) {
            return;
        }

        Long sellerId = listing.getUser() == null ? null : listing.getUser().getId();
        String message = "The listing '" + listing.getTitle() + "' you saved was updated: "
                + String.join(", ", changedFields) + ".";
        for (Favorite favorite : favoriteRepository.findByListingId(listing.getId())) {
            if (favorite.getUserId().equals(sellerId)) {
                continue;
            }
            notificationService.sendToUser(favorite.getUserId(), "LISTING_UPDATED", Map.of(
                    "title", "A saved listing was updated",
                    "message", message,
                    "type", "info",
                    "listingId", listing.getId()));
        }
    }

    /**
     * Called when a listing becomes SOLD or DELISTED: notify and email users
     * who saved it, then drop those favorites so stale entries never linger.
     */
    @Transactional
    public void handleListingRemoved(Long listingId, String reason) {
        List<Favorite> favorites = favoriteRepository.findByListingId(listingId);
        if (favorites.isEmpty()) {
            return;
        }

        Listings listing = listingRepository.findById(listingId).orElse(null);
        String title = (listing != null) ? listing.getTitle() : "a listing";
        Long sellerId = listing == null || listing.getUser() == null
                ? null
                : listing.getUser().getId();
        String message = "The listing '" + title + "' you saved "
                + ("sold".equalsIgnoreCase(reason) ? "was sold." : "is no longer available.");

        for (Favorite favorite : favorites) {
            if (favorite.getUserId().equals(sellerId)) {
                continue;
            }
            notificationService.sendToUser(favorite.getUserId(), "LISTING_UNAVAILABLE", Map.of(
                    "title", "A saved listing is no longer available",
                    "message", message,
                    "type", "info",
                    "listingId", listingId));
        }
        Runnable sendEmails = () -> favorites.stream()
                .filter(favorite -> !favorite.getUserId().equals(sellerId))
                .forEach(favorite -> userRepository.findById(favorite.getUserId()).ifPresent(user -> {
                    if (user.getEmail() != null) {
                        try {
                            emailServices.sendListingUnavailableEmail(user.getEmail(), title, reason);
                        } catch (RuntimeException e) {
                            log.error("Failed to email user {} about unavailable listing {}",
                                    user.getId(), listingId, e);
                        }
                    }
                }));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEmails.run();
                }
            });
        } else {
            sendEmails.run();
        }
        favoriteRepository.deleteByListingId(listingId);
    }
}
