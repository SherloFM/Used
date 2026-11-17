package com.example.marketplace.tests;

import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.exceptions.ResourceNotFoundException;
import com.example.marketplace.model.Listings;
import com.example.accounts.model.User;
import com.example.marketplace.repository.ListingRepository;
import com.example.accounts.service.CurrentUserService;
import com.example.notification.service.EmailServices;
import com.example.marketplace.service.ListingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ListingServiceTest {
    @Mock
    private ListingRepository listingRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private EmailServices emailServices;

    @InjectMocks
    private ListingService listingService;


    @Test
    void deleteListing_activeListing_becomesDelisted() {

        User user = new User();
        user.setId(1L);
        user.setRole(User.Role.USER);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUser(user);

        when(currentUserService.getCurrentUser())
                .thenReturn(user);

        when(listingRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(listing));

        listingService.deleteListing(10L);
        assertEquals(
                Listings.Status.DELISTED,
                listing.getStatus()
        );

        verify(listingRepository)
                .save(listing);
    }

    @Test
    void deleteListing_soldListing_isRejected() {

        User user = new User();
        user.setId(1L);
        user.setRole(User.Role.USER);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.SOLD);
        listing.setUser(user);

        when(currentUserService.getCurrentUser())
                .thenReturn(user);

        when(listingRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(listing));

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> listingService.deleteListing(10L)
                );

        assertEquals(
                "Only active listings can be removed",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .save(any());
    }

    @Test
    void deleteListing_delistedListing_isRejected() {

        User user = new User();
        user.setId(1L);
        user.setRole(User.Role.USER);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.DELISTED);
        listing.setUser(user);

        when(currentUserService.getCurrentUser())
                .thenReturn(user);

        when(listingRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(listing));

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> listingService.deleteListing(10L)
                );

        assertEquals(
                "Only active listings can be removed",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .save(any());
    }

    @Test
    void deleteListing_otherUsersListing_isRejected() {

        User user = new User();
        user.setId(1L);
        user.setRole(User.Role.USER);

        when(currentUserService.getCurrentUser())
                .thenReturn(user);

        when(listingRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> listingService.deleteListing(10L)
                );

        assertEquals(
                "Listing not found or you are not the owner",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .save(any());
    }

    @Test
    void deleteListing_admin_isRejected() {

        User admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

        when(currentUserService.getCurrentUser())
                .thenReturn(admin);

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> listingService.deleteListing(10L)
                );

        assertEquals(
                "Use the admin listing removal endpoint",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .findByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void adminDeleteListing_activeListing_becomesDelisted() {

        User admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

        User seller = new User();
        seller.setId(2L);
        seller.setEmail("seller@example.com");

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setTitle("Phone");
        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUser(seller);

        when(currentUserService.getCurrentUser())
                .thenReturn(admin);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.of(listing));

        listingService.adminDeleteListing(10L);

        assertEquals(
                Listings.Status.DELISTED,
                listing.getStatus()
        );

        verify(listingRepository)
                .save(listing);

        verify(emailServices)
                .sendListingDeletedEmail(
                        "seller@example.com",
                        "Phone"
                );
    }

    @Test
    void adminDeleteListing_soldListing_isRejected() {

        User admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

        Listings listing = new Listings();
        listing.setId(10L);
        listing.setStatus(Listings.Status.SOLD);

        when(currentUserService.getCurrentUser())
                .thenReturn(admin);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.of(listing));

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> listingService.adminDeleteListing(10L)
                );

        assertEquals(
                "A sold listing cannot be removed",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .save(any());

        verify(emailServices, never())
                .sendListingDeletedEmail(anyString(), anyString());
    }

    @Test
    void adminDeleteListing_normalUser_isRejected() {

        User user = new User();
        user.setId(1L);
        user.setRole(User.Role.USER);

        when(currentUserService.getCurrentUser())
                .thenReturn(user);

        InformationExistException exception =
                assertThrows(
                        InformationExistException.class,
                        () -> listingService.adminDeleteListing(10L)
                );

        assertEquals(
                "Only admins can remove listings",
                exception.getMessage()
        );

        verify(listingRepository, never())
                .findById(anyLong());
    }

    @Test
    void adminDeleteListing_listingDoesNotExist_throwsException() {

        User admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

        when(currentUserService.getCurrentUser())
                .thenReturn(admin);

        when(listingRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> listingService.adminDeleteListing(10L)
        );

        verify(listingRepository, never())
                .save(any());
    }
}
