package com.example.Used.Tests;

import com.example.Used.Model.Listings;
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


}
