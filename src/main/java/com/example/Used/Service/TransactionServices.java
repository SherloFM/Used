package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.AuditLog;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Transactions;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.TransactionsRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class TransactionServices {
    private final TransactionsRepository transactionsRepository;
    private final ListingRepository listingRepository;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;
    private final AuditLogService auditLogService;
    private final FavoriteService favoriteService;

    public TransactionServices(TransactionsRepository transactionsRepository,
                               ListingRepository listingRepository,
                               CurrentUserService currentUserService,
                               EmailServices emailServices,
                               AuditLogService auditLogService,
                               FavoriteService favoriteService) {
        this.transactionsRepository = transactionsRepository;
        this.listingRepository = listingRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
        this.auditLogService = auditLogService;
        this.favoriteService = favoriteService;
    }

    @Transactional
    public Transactions purchaseListing(Long listingId){
        User buyer = currentUserService.getCurrentUser();

        // Admins cannot buy
        if (buyer.getRole() == User.Role.ADMIN) {
            throw new InformationExistException(
                    "Admins cannot buy listings"
            );
        }

        Listings listing = listingRepository.findById(listingId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Listing not found"
                        )
                );

        // User cannot buy their own listing
        if (listing.getUser().getId().equals(buyer.getId())) {
            throw new InformationExistException(
                    "You cannot buy your own listing"
            );
        }

        // Listing must still be available... this is replaced by the atomic claim below.
        // Capture BEFORE the claim: updateStatusIfCurrent clears the persistence
        // context, and after it the listing is detached, so lazy fields like
        // listing.getUser() could no longer be read.
        User seller = listing.getUser();
        Long sellerId = seller.getId();
        String sellerEmail = seller.getEmail();
        String listingTitle = listing.getTitle();
        double price = listing.getPrice();

        // Atomic claim replaces read-then-set: exactly one buyer can flip
        // ACTIVE -> SOLD. Every other concurrent buyer updates 0 rows and
        // gets a clean business error instead of the database's
        // transaction-unique violation.
        int claimed = listingRepository.updateStatusIfCurrent(
                listingId, Listings.Status.ACTIVE, Listings.Status.SOLD);
        if (claimed == 0) {
            throw new InformationExistException(
                    "Listing is no longer available for purchase"
            );
        }

        Transactions transaction = new Transactions();

        transaction.setListing(listing);          // detached is fine: no cascade, only the FK id is written
        transaction.setBuyer(buyer);
        transaction.setSeller(seller);
        transaction.setAmount(price);
        transaction.setStatus(Transactions.Status.COMPLETED);

        Transactions savedTransaction =
                transactionsRepository.save(transaction);

        // Notify users who saved this listing, then drop their favorites
        favoriteService.handleListingRemoved(listingId, "sold");


        auditLogService.log(
                AuditLog.AuditAction.LISTING_SOLD,
                buyer,
                "Listing '" + listingTitle + "' (id=" + listingId + ") sold for "
                        + price + " to user id=" + buyer.getId()
                        + " from seller id=" + sellerId,
                sellerId
        );

        // Notify seller
        emailServices.sendListingSoldEmail(
                sellerEmail,
                listingTitle
        );

        return savedTransaction;
    }


}
