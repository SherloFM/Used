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

    public TransactionServices(TransactionsRepository transactionsRepository,
                               ListingRepository listingRepository,
                               CurrentUserService currentUserService,
                               EmailServices emailServices,
                               AuditLogService auditLogService) {
        this.transactionsRepository = transactionsRepository;
        this.listingRepository = listingRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
        this.auditLogService = auditLogService;
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

        // Listing must still be available
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException(
                    "Listing is no longer available for purchase"
            );
        }
        User seller = listing.getUser();

        Transactions transaction = new Transactions();

        transaction.setListing(listing);
        transaction.setBuyer(buyer);
        transaction.setSeller(seller);
        transaction.setAmount(listing.getPrice());
        transaction.setStatus(Transactions.Status.COMPLETED);

        Transactions savedTransaction =
                transactionsRepository.save(transaction);
        // Mark listing as sold
        listing.setStatus(Listings.Status.SOLD);

        listingRepository.save(listing);


        auditLogService.log(
                AuditLog.AuditAction.LISTING_SOLD,
                buyer,
                "Listing '" + listing.getTitle() + "' (id=" + listingId + ") sold for "
                        + listing.getPrice() + " to user id=" + buyer.getId()
                        + " from seller id=" + seller.getId()
        );

        // Notify seller
        emailServices.sendListingSoldEmail(
                seller.getEmail(),
                listing.getTitle()
        );

        return savedTransaction;
    }


}
