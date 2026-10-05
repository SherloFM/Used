package com.example.Used.Tests;

import com.example.Used.Model.Listings;
import com.example.Used.Model.Transactions;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.TransactionsRepository;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.EmailServices;
import com.example.Used.Service.TransactionServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionServicesTest {
    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private EmailServices emailServices;

    @InjectMocks
    private TransactionServices transactionServices;

    @Test
    void purchaseListing_activeListing_purchaseSucceeds() {

        User buyer = new User();
        buyer.setId(1L);
        buyer.setRole(User.Role.USER);

        User seller = new User();
        seller.setId(2L);
        seller.setRole(User.Role.USER);
        seller.setEmail("seller@example.com");

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setTitle("Phone");
        listing.setPrice(200);
        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUser(seller);

        Transactions savedTransaction = new Transactions();
        savedTransaction.setId(100L);
        savedTransaction.setListing(listing);
        savedTransaction.setBuyer(buyer);
        savedTransaction.setSeller(seller);
        savedTransaction.setAmount(200);
        savedTransaction.setStatus(
                Transactions.Status.COMPLETED
        );

        when(currentUserService.getCurrentUser())
                .thenReturn(buyer);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.of(listing));

        when(transactionsRepository.save(any(Transactions.class)))
                .thenReturn(savedTransaction);

        Transactions result =
                transactionServices.purchaseListing(10L);

        assertEquals(
                Transactions.Status.COMPLETED,
                result.getStatus()
        );

        assertEquals(
                buyer,
                result.getBuyer()
        );

        assertEquals(
                seller,
                result.getSeller()
        );

        assertEquals(
                200,
                result.getAmount()
        );

        assertEquals(
                Listings.Status.SOLD,
                listing.getStatus()
        );

        verify(listingRepository)
                .save(listing);

        verify(transactionsRepository)
                .save(any(Transactions.class));

        verify(emailServices)
                .sendListingSoldEmail(
                        "seller@example.com",
                        "Phone"
                );
    }



}
