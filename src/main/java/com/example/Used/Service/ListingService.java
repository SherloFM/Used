package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Requests.ListingSearchRequests;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.ListingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ListingService {

    private final ListingRepository listingsRepository;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;

    @Autowired
    public ListingService(
            ListingRepository listingsRepository,
            CurrentUserService currentUserService,
            EmailServices emailServices
    ) {
        this.listingsRepository = listingsRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
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

        // Get owner from logged-in user
        listing.setUser(user);

        listingsRepository.save(listing);

        return listing;
    }

    // GET ONE LISTING
    public Listings getListing(Long id){

        Listings listing = listingsRepository.findById(id).orElseThrow(
                ()-> new InformationExistException("no listing")
        );
        return listing;
    }

    // GET ALL LISTINGS
    public List<Listings> getListings() {

        return listingsRepository.findAll();
    }

    // GET LISTINGS THAT ARE FOR SALE
    public List<Listings> getListingsForSale() {

        return listingsRepository.findByStatus(Listings.Status.ACTIVE, Sort.unsorted());
    }

    // UPDATE LISTING
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
                .findByIdAndUserId(id, user.getId())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Only active listings can be edited"
            );
        }


        listing.setTitle(listingRequest.getTitle());
        listing.setDescription(listingRequest.getDescription());
        listing.setPrice(listingRequest.getPrice());
        listing.setCondition(listingRequest.getCondition());
        listing.setLocation(listingRequest.getLocation());

        listingsRepository.save(listing);

        return listing;
    }

    // DELETE LISTING
    public void deleteListing(Long id) {

        User user = currentUserService.getCurrentUser();

        if (user.getRole() == User.Role.ADMIN) {
            throw new InformationExistException(
                    "Use the admin listing removal endpoint"
            );
        }

        Listings listing = listingsRepository
                .findByIdAndUserId(id, user.getId())
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

        listingsRepository.save(listing);
    }

    public void adminDeleteListing(Long id) {

        User admin = currentUserService.getCurrentUser();

        if (admin.getRole() != User.Role.ADMIN) {
            throw new InformationExistException(
                    "Only admins can remove listings"
            );
        }

        Listings listing = listingsRepository.findById(id)
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

        String sellerEmail = listing.getUser().getEmail();
        String listingTitle = listing.getTitle();

        listing.setStatus(Listings.Status.DELISTED);

        listingsRepository.save(listing);

        // Notify owner
        emailServices.sendListingDeletedEmail(
                sellerEmail,
                listingTitle
        );
    }

    // UPLOAD / CHANGE LISTING IMAGE
    public Listings uploadImage(
            Long id,
            MultipartFile img
    ) throws IOException {

        User user = currentUserService.getCurrentUser();

        Listings listing = listingsRepository
                .findByIdAndUserId(id, user.getId())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        listing.setImg(img.getBytes());
        listing.setImgtype(img.getContentType());

        listingsRepository.save(listing);

        return listing;
    }

    public List<Listings> searchListings(ListingSearchRequests request) {
        // 1. Handle Keyword (Default to empty string to avoid SQL bytea null bug)
        String keyword = (request.getKeyword() != null) ? request.getKeyword().trim() : "";

        // 2. Handle Categories
        List<Long> categoryIds = request.getCategoryIds();
        boolean hasCategories = (categoryIds != null && !categoryIds.isEmpty());

        // If no categories are provided, pass a dummy list so the query doesn't fail
        if (!hasCategories) {
            categoryIds = new ArrayList<>();
        }

        // 3. Handle Sorting (Price, Condition, or Both)
        String sortDirStr = (request.getSortDir() != null && request.getSortDir().equalsIgnoreCase("desc"))
                ? "desc" : "asc";
        Sort.Direction direction = sortDirStr.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

        Sort sort;
        String sortBy = request.getSortBy();

        if (sortBy != null && !sortBy.trim().isEmpty()) {
            // If user specifies a field, sort by it
            // Note: If they want "both", we can default to price then condition
            if (sortBy.equalsIgnoreCase("price")) {
                sort = Sort.by(direction, "price").and(Sort.by(direction, "condition"));
            } else if (sortBy.equalsIgnoreCase("condition")) {
                sort = Sort.by(direction, "condition").and(Sort.by(direction, "price"));
            } else {
                // Fallback to price if they type something else
                sort = Sort.by(direction, "price");
            }
        } else {
            // Default sort if body is empty or sortBy is missing
            sort = Sort.by(Sort.Direction.ASC, "price");
        }

        // 4. Execute the single unified query
        return listingsRepository.searchListingsUnified(
                Listings.Status.ACTIVE,
                keyword,
                hasCategories,
                categoryIds,
                hasCategories ? categoryIds.size() : 0,
                sort
        );
    }
}