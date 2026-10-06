package com.example.Used.Tests;

import com.example.Used.Model.Listings;
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

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionConcurrencyTest {
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
    void twoUsersCannotBuySameListing() throws Exception {

        // Two different buyers
        User buyer1 = new User();
        buyer1.setId(1L);
        buyer1.setRole(User.Role.USER);

        User buyer2 = new User();
        buyer2.setId(2L);
        buyer2.setRole(User.Role.USER);

        // Seller
        User seller = new User();
        seller.setId(3L);
        seller.setRole(User.Role.USER);
        seller.setEmail("seller@test.com");

        // Listing
        Listings listing = new Listings();
        listing.setId(100L);
        listing.setTitle("Test Listing");
        listing.setPrice(100);
        listing.setStatus(Listings.Status.ACTIVE);
        listing.setUser(seller);

        // Return the same listing to both requests
        when(listingRepository.findById(100L))
                .thenReturn(java.util.Optional.of(listing));

        // We will change the current user depending on the thread
        when(currentUserService.getCurrentUser())
                .thenAnswer(invocation -> {
                    String threadName = Thread.currentThread().getName();

                    if (threadName.equals("buyer-1")) {
                        return buyer1;
                    }

                    return buyer2;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> buyer1Purchase = () -> {
            start.await();

            try {
                transactionServices.purchaseListing(100L);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Callable<Boolean> buyer2Purchase = () -> {
            start.await();

            try {
                transactionServices.purchaseListing(100L);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> result1 =
                executor.submit(() -> {
                    Thread.currentThread().setName("buyer-1");
                    return buyer1Purchase.call();
                });

        Future<Boolean> result2 =
                executor.submit(() -> {
                    Thread.currentThread().setName("buyer-2");
                    return buyer2Purchase.call();
                });

        // Release both buyers at almost exactly the same time
        start.countDown();

        boolean buyer1Succeeded = result1.get();
        boolean buyer2Succeeded = result2.get();

        executor.shutdown();

        // Exactly ONE buyer should succeed
        assertTrue(
                buyer1Succeeded ^ buyer2Succeeded,
                "Exactly one buyer should be able to purchase the listing"
        );

        // Listing must end up SOLD
        assertEquals(
                Listings.Status.SOLD,
                listing.getStatus()
        );
    }

}
