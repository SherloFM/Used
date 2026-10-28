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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           ListingRepository listingRepository,
                           UserRepository userRepository,
                           CurrentUserService currentUserService,
                           EmailServices emailServices) {
        this.favoriteRepository = favoriteRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
    }

    @Transactional
    public FavoriteResponse addFavorite(Long listingId) {
        User me = currentUserService.getCurrentUser();

        Listings listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getUser() != null && listing.getUser().getId().equals(me.getId())) {
            throw new InformationExistException("You cannot favorite your own listing");
        }

        if (favoriteRepository.existsByUserIdAndListingId(me.getId(), listingId)) {
            throw new InformationExistException("Listing is already in your favorites");
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(me.getId());
        favorite.setListingId(listingId);
        Favorite saved = favoriteRepository.save(favorite);

        // Phase 3 hook: notify the seller via NotificationService.sendToUser(...)

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

    /**
     * Called when a listing becomes SOLD or DELISTED: email every user who
     * saved it, then drop those favorites so stale entries never linger.
     */
    @Transactional
    public void handleListingRemoved(Long listingId, String reason) {
        List<Favorite> favorites = favoriteRepository.findByListingId(listingId);
        if (favorites.isEmpty()) {
            return;
        }

        Listings listing = listingRepository.findById(listingId).orElse(null);
        String title = (listing != null) ? listing.getTitle() : "a listing";

        for (Favorite favorite : favorites) {
            userRepository.findById(favorite.getUserId()).ifPresent(user -> {
                if (user.getEmail() != null) {
                    emailServices.sendListingUnavailableEmail(user.getEmail(), title, reason);
                }
            });
        }
        favoriteRepository.deleteByListingId(listingId);
    }
}
