package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.ListingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

        return listingsRepository.findByStatus(Listings.Status.ACTIVE);
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

        Listings listing = listingsRepository
                .findByIdAndUserId(id, user.getId())
                .orElseThrow(() ->
                        new InformationExistException(
                                "Listing not found or you are not the owner"
                        ));

        listingsRepository.delete(listing);
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

    // BUY LISTING
    public Listings buyListing(Long id){

        User buyer = currentUserService.getCurrentUser();

        Listings listing = listingsRepository.findById(id).orElseThrow(() ->
                        new ResourceNotFoundException("Listing not found"));

        // Don't allow someone to buy their own listing
        if (listing.getUser().getId().equals(buyer.getId())) {
            throw new InformationExistException(
                    "You cannot buy your own listing"
            );
        }

        // Make sure listing is actually available
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Listing is not available for purchase"
            );
        }

        listing.setStatus(Listings.Status.SOLD);

        listingsRepository.save(listing);
        return listing;
    }
}