package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.AuditLog;
import com.example.Used.Model.Categories;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Requests.ListingSearchRequests;
import com.example.Used.Model.Responses.MyListingResponse;
import com.example.Used.Model.Transactions;
import com.example.Used.Model.User;
import com.example.Used.Repository.CategoryRepository;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.TransactionsRepository;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private final AuditLogService auditLogService;
    private final ListingRepository listingsRepository;
    private final TransactionsRepository transactionsRepository;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;
    private final FavoriteService favoriteService;
    public final CategoryRepository categoryRepository;

    @Autowired
    public ListingService(
            ListingRepository listingsRepository,
            TransactionsRepository transactionsRepository,
            CurrentUserService currentUserService,
            EmailServices emailServices,
            AuditLogService auditLogService,
            CategoryRepository categoryRepository,
            FavoriteService favoriteService
    ) {
        this.listingsRepository = listingsRepository;
        this.transactionsRepository = transactionsRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
        this.auditLogService = auditLogService;
        this.categoryRepository = categoryRepository;
        this.favoriteService = favoriteService;
    }

    // CREATE LISTING
    public Listings createListing(Listings listingRequest) {

        User user = currentUserService.getCurrentUser();

        if (user.getRole() == User.Role.ADMIN){
            throw new InformationExistException("Admins cannot create listings");
        }

        Listings listing = new Listings();

        listing.setTitle(listingRequest.getTitle());
        listing.setDescription(listingRequest.getDescription());
        listing.setPrice(listingRequest.getPrice());
        listing.setCondition(listingRequest.getCondition());
        listing.setLocation(listingRequest.getLocation());

        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUser(user);

        if (listingRequest.getCategories() != null && !listingRequest.getCategories().isEmpty()) {
            Set<Categories> categorySet = new HashSet<>();

            for (Categories cat : listingRequest.getCategories()) {
                // Fetch fresh from DB to ensure valid ID and avoid detached entity issues
                Categories dbCat = categoryRepository.findById(cat.getId()).orElse(null);
                if (dbCat != null) {
                    categorySet.add(dbCat);
                }
            }
            listing.setCategories(categorySet);
        } else {
            listing.setCategories(new HashSet<>());
        }

        auditLogService.log(
                AuditLog.AuditAction.LISTING_CREATED,
                user,
                "Created listing '" + listing.getTitle() + "' (id=" + listing.getId() + ")"
        );

        listingsRepository.save(listing);

        return listing;
    }

    // GET ONE LISTING
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Listings getListing(Long id){

        Listings listing = listingsRepository.findByIdWithUser(id).orElseThrow(
                ()-> new InformationExistException("no listing")
        );
        return listing;
    }

    // GET ALL LISTINGS
    public List<Listings> getListings() {

        return listingsRepository.findAll();
    }

    // UPDATE LISTING
    @org.springframework.transaction.annotation.Transactional
    public Listings updateListing(
            Long id,
            Listings listingRequest
    ) {

        User user = currentUserService.getCurrentUser();

        if (user.getRole() == User.Role.ADMIN) {
            throw new InformationExistException(
                    "Admins cannot edit listings"
            );
        }

        Listings listing = listingsRepository
                .findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Only active listings can be edited"
            );
        }

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(listing.getTitle(), listingRequest.getTitle())) {
            changedFields.add("title");
        }
        if (!Objects.equals(listing.getDescription(), listingRequest.getDescription())) {
            changedFields.add("description");
        }
        if (Double.compare(listing.getPrice(), listingRequest.getPrice()) != 0) {
            changedFields.add("price");
        }
        if (!Objects.equals(listing.getCondition(), listingRequest.getCondition())) {
            changedFields.add("condition");
        }
        if (!Objects.equals(listing.getLocation(), listingRequest.getLocation())) {
            changedFields.add("location");
        }

        listing.setTitle(listingRequest.getTitle());
        listing.setDescription(listingRequest.getDescription());
        listing.setPrice(listingRequest.getPrice());
        listing.setCondition(listingRequest.getCondition());
        listing.setLocation(listingRequest.getLocation());

        listingsRepository.save(listing);
        if (!changedFields.isEmpty()) {
            favoriteService.notifyListingUpdated(listing, changedFields);
        }

        auditLogService.log(
                AuditLog.AuditAction.LISTING_UPDATED,
                user,
                "Updated listing '" + listing.getTitle() + "' (id=" + listing.getId() + ")"
        );

        return listing;
    }

    // DELETE LISTING
    @org.springframework.transaction.annotation.Transactional
    public void deleteListing(Long id) {

        User user = currentUserService.getCurrentUser();

        if (user.getRole() == User.Role.ADMIN) {
            throw new InformationExistException(
                    "Use the admin listing removal endpoint"
            );
        }

        Listings listing = listingsRepository
                .findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Only active listings can be removed"
            );
        }

        listing.setStatus(Listings.Status.DELISTED);
        listing.setStatusReason("Removed by you");

        listingsRepository.save(listing);

        // Notify users who saved this listing, then drop their favorites
        favoriteService.handleListingRemoved(id, "delisted");

        // AUDIT: listing delisted by owner
        auditLogService.log(
                AuditLog.AuditAction.LISTING_DELISTED,
                user,
                "Owner cancelled listing '" + listing.getTitle() + "' (id=" + id + ")"
        );
    }

    /** Kept for existing callers: default reason when no specific one applies. */
    public void adminDeleteListing(Long id) {
        adminDeleteListing(id, "Removed by administrator");
    }

    @org.springframework.transaction.annotation.Transactional
    public void adminDeleteListing(Long id, String reason) {

        User admin = currentUserService.getCurrentUser();

        if (admin.getRole() != User.Role.ADMIN) {
            throw new InformationExistException(
                    "Only admins can remove listings"
            );
        }

        Listings listing = listingsRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Listing not found"
                        )
                );

        // Admin cannot remove something that is already sold
        if (listing.getStatus() == Listings.Status.SOLD) {
            throw new InformationExistException(
                    "A sold listing cannot be removed"
            );
        }
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException("Listing has already been removed");
        }

        String sellerEmail = listing.getUser().getEmail();
        String listingTitle = listing.getTitle();

        listing.setStatus(Listings.Status.DELISTED);
        listing.setStatusReason(reason);

        listingsRepository.save(listing);

        // Notify users who saved this listing, then drop their favorites
        favoriteService.handleListingRemoved(id, "delisted");

        // Notify owner
        emailServices.sendListingDeletedEmail(
                sellerEmail,
                listingTitle
        );

        auditLogService.log(
                AuditLog.AuditAction.LISTING_DELISTED,
                admin,
                "Admin removed listing '" + listingTitle + "' (id=" + id + ")",
                listing.getUser().getId()
        );
    }

    // MY LISTINGS (profile page tab)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MyListingResponse> getMyListings() {
        User me = currentUserService.getCurrentUser();

        List<Listings> listings = listingsRepository.findByUserIdOrderByCreatedAtDesc(me.getId());

        // One query fetches the buyer usernames of every sold listing (no N+1)
        List<Long> soldIds = listings.stream()
                .filter(l -> l.getStatus() == Listings.Status.SOLD)
                .map(Listings::getId)
                .toList();
        Map<Long, Transactions> salesByListing = soldIds.isEmpty()
                ? Map.of()
                : transactionsRepository.findWithBuyerByListingIdIn(soldIds).stream()
                    .collect(Collectors.toMap(t -> t.getListing().getId(), t -> t));

        return listings.stream()
                .map(l -> MyListingResponse.from(l, salesByListing.get(l.getId())))
                .toList();
    }

    // UPLOAD / CHANGE LISTING IMAGE
    @org.springframework.transaction.annotation.Transactional
    public Listings uploadImage(
            Long id,
            MultipartFile img
    ) throws IOException {

        User user = currentUserService.getCurrentUser();

        Listings listing = listingsRepository
                .findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        byte[] imageBytes = img.getBytes();
        boolean imageChanged = !Arrays.equals(listing.getImg(), imageBytes);
        listing.setImg(imageBytes);
        listing.setImgtype(img.getContentType());

        listingsRepository.save(listing);
        if (imageChanged) {
            favoriteService.notifyListingUpdated(listing, List.of("image"));
        }

        return listing;
    }

    public Page<Listings> searchListings(
            String keyword,
            List<Long> categoryIds,
            String sortBy,
            String sortDir,
            int page,
            int size
    ) {
        String cleanKeyword = (keyword != null) ? keyword.trim() : "";
        boolean hasCategories = (categoryIds != null && !categoryIds.isEmpty());

        if (!hasCategories) {
            categoryIds = new ArrayList<>();
        }

        // Determine Sort Direction
        String cleanSortDir = (sortDir != null && sortDir.equalsIgnoreCase("desc")) ? "desc" : "asc";
        Sort.Direction direction = cleanSortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

        // Build Sort Object
        Sort sort;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            if (sortBy.equalsIgnoreCase("price")) {
                sort = Sort.by(direction, "price").and(Sort.by(direction, "condition"));
            } else if (sortBy.equalsIgnoreCase("condition")) {
                sort = Sort.by(direction, "condition").and(Sort.by(direction, "price"));
            } else {
                sort = Sort.by(direction, "price");
            }
        } else {
            // Default sort by newest first (createdAt desc) is usually better for marketplaces
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        // Create Pageable object
        Pageable pageable = PageRequest.of(page, size, sort);

        // Execute Query
        return listingsRepository.searchListingsPaginated(
                Listings.Status.ACTIVE,
                cleanKeyword,
                hasCategories,
                categoryIds,
                hasCategories ? categoryIds.size() : 0,
                pageable
        );

    }
}