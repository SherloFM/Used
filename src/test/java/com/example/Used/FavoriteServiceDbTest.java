package com.example.Used;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.Favorite;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Responses.FavoriteResponse;
import com.example.Used.Model.User;
import com.example.Used.Repository.FavoriteRepository;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Security.MyUserDetails;
import com.example.Used.Service.FavoriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class FavoriteServiceDbTest {

    @Autowired private FavoriteService favoriteService;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private FavoriteRepository favoriteRepository;

    private User buyer;          // carol_buyer
    private User seller;         // bob_seller
    private Listings activeListing; // listing 34

    @BeforeEach
    void setUp() {
        buyer = userRepository.findByEmail("carol@used.local");
        seller = userRepository.findByEmail("bob@used.local");
        activeListing = listingRepository.findById(34L)
                .orElseThrow(() -> new AssertionError("Seed listing 34 missing"));
        loginAs(buyer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        MyUserDetails details = new MyUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void aUserCanFavoriteAnActiveListing() {
        FavoriteResponse response = favoriteService.addFavorite(activeListing.getId());
        assertNotNull(response.getId());
        assertEquals(activeListing.getId(), response.getListingId());
        assertTrue(favoriteService.isFavorite(activeListing.getId()));
    }

    @Test
    void aUserCannotFavoriteTheSameListingTwice() {
        favoriteService.addFavorite(activeListing.getId());
        assertThrows(InformationExistException.class,
                () -> favoriteService.addFavorite(activeListing.getId()));
    }

    @Test
    void aUserCannotFavoriteTheirOwnListing() {
        Listings own = new Listings();
        own.setTitle("Carol's item");
        own.setDescription("desc");
        own.setPrice(10.0);
        own.setCondition("Used");
        own.setLocation("Manama");
        own.setStatus(Listings.Status.ACTIVE);
        own.setUser(buyer);
        Listings saved = listingRepository.save(own);

        assertThrows(InformationExistException.class,
                () -> favoriteService.addFavorite(saved.getId()));
    }

    @Test
    void removingAFavoriteThatDoesNotExistThrows() {
        assertThrows(ResourceNotFoundException.class,
                () -> favoriteService.removeFavorite(activeListing.getId()));
    }

    @Test
    void removingAnExistingFavoriteWorks() {
        favoriteService.addFavorite(activeListing.getId());
        favoriteService.removeFavorite(activeListing.getId());
        assertFalse(favoriteService.isFavorite(activeListing.getId()));
    }

    @Test
    void handleListingRemovedDeletesFavorites() {
        favoriteService.addFavorite(activeListing.getId());
        assertTrue(favoriteRepository.existsByUserIdAndListingId(buyer.getId(), activeListing.getId()));
        favoriteService.handleListingRemoved(activeListing.getId(), "sold");
        assertFalse(favoriteRepository.existsByUserIdAndListingId(buyer.getId(), activeListing.getId()));
    }
}
