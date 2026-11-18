package com.example.marketplace.security;

import com.example.marketplace.controller.InternalListingController;
import com.example.marketplace.model.Listings;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.service.ListingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

class InternalListingSecurityTest {
    private static final String TOKEN = "test-internal-token-at-least-32-characters";
    private static final byte[] KEY = "test-marketplace-key-with-32-bytes".getBytes(StandardCharsets.UTF_8);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ListingRepository listings;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "internal.api-token=" + TOKEN,
                "security.jwt.secret=" + Base64.getEncoder().encodeToString(KEY),
                "security.jwt.issuer=used-accounts", "security.jwt.audience=used-api",
                "security.auth-cookie.name=used_auth", "security.auth-cookie.secure=false",
                "security.cors.allowed-origins=http://localhost:8080");
        context.register(Config.class);
        context.refresh();
        listings = context.getBean(ListingRepository.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void close() { context.close(); }

    private Cookie user(String role) {
        String token = Jwts.builder().setSubject("12").setIssuer("used-accounts").setAudience("used-api")
                .claim("roles", List.of(role)).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(KEY)).compact();
        return new Cookie("used_auth", token);
    }

    @Test
    void serviceTokenReturnsOnlyTheSummaryFieldsForEachListingStatus() throws Exception {
        Listings listing = new Listings();
        listing.setId(7L);
        listing.setUserId(12L);
        listing.setTitle("Bike");
        listing.setPrice(150.0);
        listing.setImgUrl("/images/bike.jpg");
        listing.setDescription("Not part of the internal summary");
        when(listings.findById(7L)).thenReturn(Optional.of(listing));
        for (Listings.Status state : Listings.Status.values()) {
            listing.setStatus(state);
            mvc.perform(get("/api/internal/listings/7").header("X-Internal-Token", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.sellerId").value(12))
                    .andExpect(jsonPath("$.title").value("Bike"))
                    .andExpect(jsonPath("$.price").value(150.0))
                    .andExpect(jsonPath("$.status").value(state.name()))
                    .andExpect(jsonPath("$.imgUrl").value("/images/bike.jpg"));
        }
        verify(listings, times(3)).findById(7L);
        verify(listings, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void missingListingReturns404() throws Exception {
        when(listings.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/internal/listings/99").header("X-Internal-Token", TOKEN))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test
    void missingWrongAndCookieOnlyCredentialsNeverReachTheRepository() throws Exception {
        mvc.perform(get("/api/internal/listings/7")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/listings/7").header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("USER", "ADMIN")) {
            mvc.perform(get("/api/internal/listings/7").cookie(user(role)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/internal/listings/7").cookie(user(role))
                            .header("X-Internal-Token", "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(listings);
    }

    @Test
    void serviceAuthenticationTakesPrecedenceOverAnInvalidCookie() throws Exception {
        when(listings.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/internal/listings/99").header("X-Internal-Token", TOKEN)
                        .cookie(new Cookie("used_auth", "invalid")))
                .andExpect(status().isNotFound());
        verify(listings).findById(99L);
    }

    @Test
    void internalCsrfExemptionDoesNotApplyToBrowserWrites() throws Exception {
        mvc.perform(post("/api/internal/test").header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk());
        mvc.perform(post("/api/internal/test")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/profile").cookie(user("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/listings/mine").header("X-Internal-Token", TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdministratorsCanReachWalletFunding() throws Exception {
        mvc.perform(post("/api/wallet/admin/42/fund").cookie(user("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/wallet/admin/42/fund").cookie(user("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void shortOrMissingSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter(null));
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter("short"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, InternalListingController.class, Probe.class})
    static class Config {
        @Bean ListingRepository listingRepository() { return mock(ListingRepository.class); }
        // The controller delegates the delist body to ListingService (8A). This
        // slice only cares about who may reach the endpoint, so the service is
        // a mock here; the delist behaviour has its own tests.
        @Bean ListingService listingService() { return mock(ListingService.class); }
    }

    @RestController
    static class Probe {
        @PostMapping("/api/internal/test") String internal() { return "ok"; }
        @PostMapping("/api/profile") String update() { return "ok"; }
        @PostMapping("/api/wallet/admin/{userId}/fund") String fund(@PathVariable Long userId) { return "ok"; }
        @GetMapping("/api/listings/mine") String mine() { return "ok"; }
    }
}
