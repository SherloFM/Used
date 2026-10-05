package com.example.Used.Tests;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.EmailServices;
import com.example.Used.Service.ListingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void getListingsForSale_returnsOnlyActiveListings() {

        Listings activeListing = new Listings();
        activeListing.setStatus(Listings.Status.ACTIVE);

        when(listingRepository.findByStatus(Listings.Status.ACTIVE))
                .thenReturn(List.of(activeListing));

        List<Listings> result =
                listingService.getListingsForSale();

        assertEquals(1, result.size());

        assertEquals(
                Listings.Status.ACTIVE,
                result.get(0).getStatus()
        );

        verify(listingRepository)
                .findByStatus(Listings.Status.ACTIVE);
    }

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




}
