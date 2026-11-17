package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.AuditClient;
import com.example.marketplace.client.UserSummary;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.exceptions.ResourceNotFoundException;
import com.example.marketplace.messaging.ListingEvent;
import com.example.marketplace.messaging.ListingLifecycle;
import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Transactions;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.TransactionsRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionServices {
    private final TransactionsRepository transactionsRepository;
    private final ListingRepository listingRepository;
    private final CurrentActorProvider currentActorProvider;
    private final AuditClient auditClient;
    private final ListingLifecycle listingLifecycle;

    public TransactionServices(TransactionsRepository transactionsRepository,
                               ListingRepository listingRepository,
                               CurrentActorProvider currentActorProvider,
                               AuditClient auditClient,
                               ListingLifecycle listingLifecycle) {
        this.transactionsRepository = transactionsRepository;
        this.listingRepository = listingRepository;
        this.currentActorProvider = currentActorProvider;
        this.auditClient = auditClient;
        this.listingLifecycle = listingLifecycle;
    }

    @Transactional
    public Transactions purchaseListing(Long listingId) {
        CurrentActor buyer = currentActorProvider.get();

        // Admins cannot buy
        if (buyer.isAdmin()) {
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
        if (listing.getUserId().equals(buyer.id())) {
            throw new InformationExistException(
                    "You cannot buy your own listing"
            );
        }

        // Capture the price BEFORE the claim: the bulk status update clears the
        // persistence context, so the entity is detached afterwards.
        Long sellerId = listing.getUserId();
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

        // Re-read after the claim: the event must describe the SOLD row, not the
        // detached ACTIVE snapshot captured above.
        Listings sold = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        listingLifecycle.sold(sold, ListingEvent.Source.LEGACY_BUY);

        Transactions transaction = new Transactions();

        transaction.setListing(listing);          // detached is fine: no cascade, only the FK id is written
        transaction.setBuyerId(buyer.id());
        transaction.setSellerId(sellerId);
        transaction.setAmount(price);
        transaction.setStatus(Transactions.Status.COMPLETED);

        Transactions savedTransaction =
                transactionsRepository.save(transaction);

        auditClient.log(
                "LISTING_SOLD",
                buyer.id(),
                buyer.username(),
                "Listing '" + listingTitle + "' (id=" + listingId + ") sold for "
                        + price + " to user id=" + buyer.id()
                        + " from seller id=" + sellerId,
                sellerId
        );

        return savedTransaction;
    }


}
