package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.AuditClient;
import com.example.marketplace.client.MediaClient;
import com.example.marketplace.client.MediaUploadResponse;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.messaging.ListingLifecycle;
import com.example.marketplace.model.Listings;
import com.example.marketplace.repository.CategoryRepository;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.TransactionsRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ListingServiceImageTest {

    private final ListingRepository listings = mock(ListingRepository.class);
    private final TransactionsRepository transactions = mock(TransactionsRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AccountsClient accounts = mock(AccountsClient.class);
    private final MediaClient media = mock(MediaClient.class);
    private final AuditClient audit = mock(AuditClient.class);
    private final ListingLifecycle lifecycle = mock(ListingLifecycle.class);
    private ListingService service;
    private Listings listing;

    @BeforeEach
    void setup() {
        service = new ListingService(listings, transactions, actors, accounts, media, audit,
                mock(CategoryRepository.class), lifecycle,
                new TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class)));
        listing = new Listings();
        listing.setId(5L);
        listing.setUserId(1L);
        listing.setImg(new byte[]{9, 9});
        listing.setImgtype("image/jpeg");
        listing.setImgUrl("/old/image");
        when(actors.get()).thenReturn(new CurrentActor(1L, "alice", "USER"));
        when(listings.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(listing));
    }

    @Test
    void storesMediaUrlAndClearsInlineBytes() throws IOException {
        when(media.upload(eq(1L), any())).thenReturn(response("http://localhost:8088/api/media/new"));

        Listings updated = service.uploadImage(5L, image());

        assertEquals("http://localhost:8088/api/media/new", updated.getImgUrl());
        assertNull(updated.getImg());
        assertNull(updated.getImgtype());
        verify(listings).saveAndFlush(listing);
        verify(lifecycle).updated(listing, List.of("image"));
    }

    @Test
    void mediaFailureLeavesListingImageUntouched() throws IOException {
        when(media.upload(eq(1L), any())).thenThrow(new IllegalStateException("Media unavailable"));

        assertThrows(IllegalStateException.class, () -> service.uploadImage(5L, image()));

        assertEquals("/old/image", listing.getImgUrl());
        assertArrayEquals(new byte[]{9, 9}, listing.getImg());
        verify(listings, never()).saveAndFlush(any());
        verify(lifecycle, never()).updated(any(), anyList());
    }

    @Test
    void nonOwnerCannotReachMedia() throws IOException {
        when(listings.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(InformationExistException.class, () -> service.uploadImage(5L, image()));

        verifyNoInteractions(media);
    }

    @Test
    void repeatedUrlDoesNotBroadcastAnImageChange() throws IOException {
        String url = "http://localhost:8088/api/media/same";
        listing.setImgUrl(url);
        when(media.upload(eq(1L), any())).thenReturn(response(url));

        service.uploadImage(5L, image());

        verify(lifecycle, never()).updated(any(), anyList());
    }

    private MediaUploadResponse response(String url) {
        return new MediaUploadResponse(UUID.randomUUID(), "/api/media/new", url,
                "image/png", 3, "abc");
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("img", "photo.png", "image/png", new byte[]{1, 2, 3});
    }
}
