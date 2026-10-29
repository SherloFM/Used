package com.example.Used.Tests;

import com.example.Used.Model.Favorite;
import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import com.example.Used.Model.AuditLog;
import com.example.Used.Repository.CategoryRepository;
import com.example.Used.Repository.FavoriteRepository;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.TransactionsRepository;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Service.AuditLogService;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.EmailServices;
import com.example.Used.Service.FavoriteService;
import com.example.Used.Service.ListingService;
import com.example.Used.Service.NotificationService;
import com.example.Used.Exceptions.InformationExistException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingChangeNotificationTest {

    private ListingRepository listingRepository;
    private FavoriteService favoriteService;
    private ListingService listingService;
    private AuditLogService auditLogService;
    private CurrentUserService currentUserService;
    private EmailServices emailServices;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        currentUserService = mock(CurrentUserService.class);
        favoriteService = mock(FavoriteService.class);
        auditLogService = mock(AuditLogService.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        emailServices = mock(EmailServices.class);
        TransactionsRepository transactionsRepository = mock(TransactionsRepository.class);

        User seller = new User();
        seller.setId(1L);
        seller.setRole(User.Role.USER);
        when(currentUserService.getCurrentUser()).thenReturn(seller);

        listingService = new ListingService(
                listingRepository,
                transactionsRepository,
                currentUserService,
                emailServices,
                auditLogService,
                categoryRepository,
                favoriteService);
    }

    @Test
    void unchangedListingValuesDoNotNotifyFavorites() {
        Listings existing = listing("Camera", 100.0);
        when(listingRepository.findByIdAndUserIdForUpdate(5L, 1L)).thenReturn(Optional.of(existing));

        listingService.updateListing(5L, listing("Camera", 100.0));

        verify(favoriteService, never()).notifyListingUpdated(any(), anyList());
    }

    @Test
    void changedListingValuesNotifyFavoritesWithChangedFieldsOnly() {
        Listings existing = listing("Camera", 100.0);
        when(listingRepository.findByIdAndUserIdForUpdate(5L, 1L)).thenReturn(Optional.of(existing));

        listingService.updateListing(5L, listing("Camera", 90.0));

        verify(favoriteService).notifyListingUpdated(existing, List.of("price"));
    }

    @Test
    void conditionChangeNotifiesFavorites() {
        Listings existing = listing("Camera", 100.0);
        when(listingRepository.findByIdAndUserIdForUpdate(5L, 1L)).thenReturn(Optional.of(existing));
        Listings request = listing("Camera", 100.0);
        request.setCondition("Like new");

        listingService.updateListing(5L, request);

        verify(favoriteService).notifyListingUpdated(existing, List.of("condition"));
    }

    @Test
    void imageIsOnlyReportedWhenItsBytesChange() throws Exception {
        Listings existing = listing("Camera", 100.0);
        existing.setImg(new byte[]{1, 2, 3});
        when(listingRepository.findByIdAndUserIdForUpdate(5L, 1L)).thenReturn(Optional.of(existing));
        org.springframework.web.multipart.MultipartFile image =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(image.getBytes()).thenReturn(new byte[]{1, 2, 3});

        listingService.uploadImage(5L, image);

        verify(favoriteService, never()).notifyListingUpdated(any(), anyList());
    }

    @Test
    void changedImageIsReportedToFavorites() throws Exception {
        Listings existing = listing("Camera", 100.0);
        existing.setImg(new byte[]{1, 2, 3});
        when(listingRepository.findByIdAndUserIdForUpdate(5L, 1L)).thenReturn(Optional.of(existing));
        org.springframework.web.multipart.MultipartFile image =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(image.getBytes()).thenReturn(new byte[]{3, 2, 1});

        listingService.uploadImage(5L, image);

        verify(favoriteService).notifyListingUpdated(existing, List.of("image"));
    }

    @Test
    void listingUpdateNotifiesFavoritorsButNotSeller() {
        Favorite sellerFavorite = favorite(1L, 5L);
        Favorite first = favorite(11L, 5L);
        Favorite second = favorite(12L, 5L);
        FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
        ListingRepository listingRepository = mock(ListingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(favoriteRepository.findByListingId(5L)).thenReturn(List.of(sellerFavorite, first, second));

        FavoriteService service = new FavoriteService(
                favoriteRepository,
                listingRepository,
                mock(UserRepository.class),
                mock(CurrentUserService.class),
                mock(EmailServices.class),
                notificationService);

        service.notifyListingUpdated(listing("Camera", 100.0), List.of("price"));

        verify(notificationService).sendToUser(eq(11L), eq("LISTING_UPDATED"), anyMap());
        verify(notificationService).sendToUser(eq(12L), eq("LISTING_UPDATED"), anyMap());
        verify(notificationService, never()).sendToUser(eq(1L), anyString(), anyMap());
    }

    @Test
    void concurrentIdenticalEditsNotifyOnlyOnce() throws Exception {
        Listings existing = listing("Camera", 100.0);
        ReentrantLock rowLock = new ReentrantLock();
        CountDownLatch firstUpdateLocked = new CountDownLatch(1);
        CountDownLatch allowFirstUpdateToCommit = new CountDownLatch(1);
        when(listingRepository.findByIdAndUserIdForUpdate(5L, 1L)).thenAnswer(invocation -> {
            rowLock.lock();
            if (firstUpdateLocked.getCount() > 0) {
                firstUpdateLocked.countDown();
                if (!allowFirstUpdateToCommit.await(5, TimeUnit.SECONDS)) {
                    rowLock.unlock();
                    throw new IllegalStateException("Timed out waiting to commit first update");
                }
            }
            return Optional.of(existing);
        });
        doAnswer(invocation -> {
            rowLock.unlock();
            return null;
        }).when(auditLogService).log(
                eq(AuditLog.AuditAction.LISTING_UPDATED), any(User.class), anyString());

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> listingService.updateListing(5L, listing("Camera", 80.0)));
            org.junit.jupiter.api.Assertions.assertTrue(firstUpdateLocked.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> listingService.updateListing(5L, listing("Camera", 80.0)));
            allowFirstUpdateToCommit.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            allowFirstUpdateToCommit.countDown();
            executor.shutdownNow();
        }

        verify(favoriteService, times(1)).notifyListingUpdated(existing, List.of("price"));
    }

    @Test
    void adminDelistingNotifiesFavoritorsAndTargetsTheSeller() {
        User admin = new User();
        admin.setId(9L);
        admin.setRole(User.Role.ADMIN);
        when(currentUserService.getCurrentUser()).thenReturn(admin);
        Listings listing = listing("Camera", 100.0);
        listing.getUser().setEmail("seller@example.test");
        when(listingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(listing));

        listingService.adminDeleteListing(5L);

        org.junit.jupiter.api.Assertions.assertEquals(Listings.Status.DELISTED, listing.getStatus());
        verify(favoriteService).handleListingRemoved(5L, "delisted");
        verify(emailServices).sendListingDeletedEmail("seller@example.test", "Camera");
        verify(auditLogService).log(
                eq(AuditLog.AuditAction.LISTING_DELISTED),
                eq(admin),
                contains("Admin removed listing"),
                eq(1L));
    }

    @Test
    void adminDelistingAlreadyDelistedListingDoesNotSendDuplicateNotifications() {
        User admin = new User();
        admin.setId(9L);
        admin.setRole(User.Role.ADMIN);
        when(currentUserService.getCurrentUser()).thenReturn(admin);
        Listings listing = listing("Camera", 100.0);
        listing.setStatus(Listings.Status.DELISTED);
        when(listingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(listing));

        org.junit.jupiter.api.Assertions.assertThrows(
                InformationExistException.class,
                () -> listingService.adminDeleteListing(5L));

        verify(favoriteService, never()).handleListingRemoved(anyLong(), anyString());
        verify(emailServices, never()).sendListingDeletedEmail(anyString(), anyString());
    }

    @Test
    void removedListingCreatesNotificationForEachFavoritor() {
        Favorite sellerFavorite = favorite(1L, 5L);
        Favorite first = favorite(11L, 5L);
        Favorite second = favorite(12L, 5L);
        FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
        ListingRepository listingRepository = mock(ListingRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(favoriteRepository.findByListingId(5L)).thenReturn(List.of(sellerFavorite, first, second));
        Listings listing = listing("Camera", 100.0);
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));

        FavoriteService service = new FavoriteService(
                favoriteRepository,
                listingRepository,
                userRepository,
                mock(CurrentUserService.class),
                mock(EmailServices.class),
                notificationService);

        service.handleListingRemoved(5L, "sold");

        verify(notificationService).sendToUser(eq(11L), eq("LISTING_UNAVAILABLE"), anyMap());
        verify(notificationService).sendToUser(eq(12L), eq("LISTING_UNAVAILABLE"), anyMap());
        verify(notificationService, never()).sendToUser(eq(1L), anyString(), anyMap());
        verify(favoriteRepository).deleteByListingId(5L);
    }

    private static Listings listing(String title, double price) {
        Listings listing = new Listings();
        listing.setId(5L);
        listing.setTitle(title);
        listing.setDescription("A description");
        listing.setPrice(price);
        listing.setCondition("Used");
        listing.setLocation("Manama");
        listing.setStatus(Listings.Status.ACTIVE);
        User seller = new User();
        seller.setId(1L);
        listing.setUser(seller);
        return listing;
    }

    private static Favorite favorite(Long userId, Long listingId) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setListingId(listingId);
        return favorite;
    }
}
