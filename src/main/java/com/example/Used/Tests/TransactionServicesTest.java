package com.example.Used.Tests;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    void purchaseListing_soldListing_isRejected() {

        User buyer = new User();
        buyer.setId(1L);
        buyer.setRole(User.Role.USER);

        User seller = new User();
        seller.setId(2L);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.SOLD);
        listing.setUser(seller);

        when(currentUserService.getCurrentUser())
                .thenReturn(buyer);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.of(listing));

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> transactionServices.purchaseListing(10L)
                );

        assertEquals(
                "Listing is no longer available for purchase",
                exception.getMessage()
        );

        verify(transactionsRepository, never())
                .save(any());

        verify(listingRepository, never())
                .save(any());

        verify(emailServices, never())
                .sendListingSoldEmail(anyString(), anyString());
    }

    @Test
    void purchaseListing_delistedListing_isRejected() {

        User buyer = new User();
        buyer.setId(1L);
        buyer.setRole(User.Role.USER);

        User seller = new User();
        seller.setId(2L);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.DELISTED);
        listing.setUser(seller);

        when(currentUserService.getCurrentUser())
                .thenReturn(buyer);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.of(listing));

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> transactionServices.purchaseListing(10L)
                );

        assertEquals(
                "Listing is no longer available for purchase",
                exception.getMessage()
        );

        verify(transactionsRepository, never())
                .save(any());

        verify(listingRepository, never())
                .save(any());
    }

    @Test
    void purchaseListing_ownListing_isRejected() {

        User seller = new User();
        seller.setId(1L);
        seller.setRole(User.Role.USER);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUser(seller);

        when(currentUserService.getCurrentUser())
                .thenReturn(seller);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.of(listing));

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> transactionServices.purchaseListing(10L)
                );

        assertEquals(
                "You cannot buy your own listing",
                exception.getMessage()
        );

        verify(transactionsRepository, never())
                .save(any());

        verify(listingRepository, never())
                .save(any());
    }

    @Test
    void purchaseListing_admin_isRejected() {

        User admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

        when(currentUserService.getCurrentUser())
                .thenReturn(admin);

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> transactionServices.purchaseListing(10L)
                );

        assertEquals(
                "Admins cannot buy listings",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .findById(anyLong());

        verify(transactionsRepository, never())
                .save(any());
    }

    @Test
    void purchaseListing_missingListing_throwsException() {

        User buyer = new User();
        buyer.setId(1L);
        buyer.setRole(User.Role.USER);

        when(currentUserService.getCurrentUser())
                .thenReturn(buyer);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> transactionServices.purchaseListing(10L)
        );

        verify(transactionsRepository, never())
                .save(any());
    }
}
