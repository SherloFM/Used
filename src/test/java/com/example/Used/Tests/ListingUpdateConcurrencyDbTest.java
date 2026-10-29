package com.example.Used.Tests;

import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:listing_update_concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ListingUpdateConcurrencyDbTest {

    @Autowired private ListingRepository listingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long listingId;
    private Long sellerId;

    @BeforeEach
    void createListing() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            User seller = new User();
            seller.setUsername("seller");
            seller.setEmail("seller@example.test");
            seller.setRole(User.Role.USER);
            sellerId = userRepository.save(seller).getId();

            Listings listing = new Listings();
            listing.setTitle("Camera");
            listing.setDescription("Used camera");
            listing.setPrice(100.0);
            listing.setCondition("Used");
            listing.setLocation("Manama");
            listing.setStatus(Listings.Status.ACTIVE);
            listing.setUser(seller);
            listingId = listingRepository.save(listing).getId();
        });
    }

    @Test
    void concurrentIdenticalUpdatesSerializeAndOnlyOneChangesTheValue() throws Exception {
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondIsWaiting = new CountDownLatch(1);
        AtomicInteger notifications = new AtomicInteger();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> transaction.execute(status -> {
                Listings listing = listingRepository.findByIdAndUserIdForUpdate(listingId, sellerId)
                        .orElseThrow();
                firstHasLock.countDown();
                await(releaseFirst);
                if (Double.compare(listing.getPrice(), 80.0) != 0) {
                    listing.setPrice(80.0);
                    listingRepository.save(listing);
                    notifications.incrementAndGet();
                }
                return listing.getPrice();
            }));

            assertTrue(firstHasLock.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> transaction.execute(status -> {
                secondIsWaiting.countDown();
                Listings listing = listingRepository.findByIdAndUserIdForUpdate(listingId, sellerId)
                        .orElseThrow();
                if (Double.compare(listing.getPrice(), 80.0) != 0) {
                    listing.setPrice(80.0);
                    listingRepository.save(listing);
                    notifications.incrementAndGet();
                }
                return listing.getPrice();
            }));

            assertTrue(secondIsWaiting.await(5, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse(second.isDone(), "The second update should wait for the listing row lock");
            releaseFirst.countDown();

            assertEquals(80.0, first.get(5, TimeUnit.SECONDS));
            assertEquals(80.0, second.get(5, TimeUnit.SECONDS));
            assertEquals(1, notifications.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release listing lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding listing lock", e);
        }
    }
}
