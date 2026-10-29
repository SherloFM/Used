package com.example.Used;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.*;
import com.example.Used.Repository.*;
import com.example.Used.Security.MyUserDetails;
import com.example.Used.Service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Committed, uniquely named fixtures: worker transactions cannot share test rollback. */
@SpringBootTest
@ActiveProfiles("dev")
@Import(PaymentConcurrencyDbTest.MailConfig.class)
class PaymentConcurrencyDbTest {
    @Autowired PaymentService payments;
    @Autowired PaymentWorker worker;
    @Autowired TransactionServices legacyPurchases;
    @Autowired FavoriteService favorites;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ListingRepository listings;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingMail mail;
    @Autowired @Qualifier("paymentExecutor") ThreadPoolExecutor paymentExecutor;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> listingIds = new ArrayList<>();
    private User seller, buyer1, buyer2;
    private Long listingId;
    private TransactionTemplate tx;

    @TestConfiguration
    static class MailConfig {
        @Bean @Primary RecordingMail recordingMail() { return new RecordingMail(); }
    }
    static class RecordingMail extends JavaMailSenderImpl {
        final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
        @Override public void send(SimpleMailMessage message) {
            sent.add(new SimpleMailMessage(message));
        }
        @Override public void send(SimpleMailMessage... messages) {
            Arrays.stream(messages).forEach(this::send);
        }
    }

    @BeforeEach void fixtures() {
        tx = new TransactionTemplate(manager);
        mail.sent.clear();
        tx.executeWithoutResult(status -> {
            seller = user("seller", "0.00");
            buyer1 = user("buyer1", "150.00");
            buyer2 = user("buyer2", "150.00");
            listingId = listing();
        });
    }

    private User user(String name, String balance) {
        User user = new User();
        user.setUsername("race-" + name + "-" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@example.invalid");
        user.setPassword("unused-test-password");
        user.setRole(User.Role.USER);
        user.setEmailVerified(true);
        user = users.saveAndFlush(user);
        userIds.add(user.getId());
        wallets.createIfMissing(user.getId());
        wallets.credit(user.getId(), new BigDecimal(balance));
        return user;
    }

    private Long listing() {
        Listings item = new Listings();
        item.setTitle("Concurrency fixture " + UUID.randomUUID());
        item.setDescription("Temporary concurrency test fixture");
        item.setCondition("Used");
        item.setLocation("Manama");
        item.setPrice(100);
        item.setStatus(Listings.Status.ACTIVE);
        item.setUser(users.getReferenceById(seller.getId()));
        Long id = listings.saveAndFlush(item).getId();
        listingIds.add(id);
        return id;
    }

    private Long pending(User buyer, Long id) {
        return tx.execute(status -> {
            Payment p = new Payment();
            p.setBuyer(users.getReferenceById(buyer.getId()));
            p.setSeller(users.getReferenceById(seller.getId()));
            p.setListing(listings.getReferenceById(id));
            p.setAmount(new BigDecimal("100.00"));
            p.setMethod(Payment.Method.WALLET);
            p.setStatus(Payment.Status.PENDING);
            return paymentRepository.saveAndFlush(p).getId();
        });
    }

    private String execute(Long id) {
        try {
            worker.executePayment(id);
            return "COMPLETED";
        } catch (PaymentWorker.PaymentFailedException failure) {
            worker.markFailed(id, failure.getReason());
            return failure.getReason();
        }
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : List.of(first, second)) {
                futures.add(callers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "Start timed out");
                    return call.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Callers not ready");
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Long submit(User buyer) {
        MyUserDetails details = new MyUserDetails(buyer);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return payments.submitPayment(listingId, Payment.Method.WALLET).getPaymentId();
        } catch (InformationExistException rejection) {
            assertTrue(List.of("This item is already being purchased by another buyer",
                    "Listing is no longer available for purchase").contains(rejection.getMessage()));
            return -1L;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test void concurrentSubmissionsSellExactlyOnce() throws Exception {
        List<Long> results = race(() -> submit(buyer1), () -> submit(buyer2));
        int accepted = (int) results.stream().filter(id -> id > 0).count();
        assertTrue(accepted >= 1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        List<Payment> rows;
        do {
            rows = paymentRepository.findByListingIdOrderByIdAsc(listingId);
            if (rows.size() == accepted && rows.stream().allMatch(p ->
                    p.getStatus() == Payment.Status.COMPLETED || p.getStatus() == Payment.Status.FAILED)) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(accepted, rows.size());
        assertEquals(1, rows.stream().filter(p -> p.getStatus() == Payment.Status.COMPLETED).count());
        assertTrue(rows.stream().allMatch(p -> p.getStatus() == Payment.Status.COMPLETED
                || (p.getStatus() == Payment.Status.FAILED && "ALREADY_SOLD".equals(p.getFailureReason()))));
        assertSingleSale();
    }

    @Test void oneBuyerCannotOverspendAcrossTwoListings() throws Exception {
        Long second = tx.execute(status -> listing());
        Long firstPayment = pending(buyer1, listingId), secondPayment = pending(buyer1, second);
        List<String> outcomes = race(() -> execute(firstPayment), () -> execute(secondPayment));
        assertEquals(1, Collections.frequency(outcomes, "COMPLETED"));
        assertEquals(1, Collections.frequency(outcomes, "INSUFFICIENT_FUNDS"));
        balance(buyer1, "50.00");
        balance(seller, "100.00");
        long sold = listingIds.stream().filter(id -> listings.findById(id).orElseThrow()
                .getStatus() == Listings.Status.SOLD).count();
        assertEquals(1, sold);
    }

    @Test void insufficientFundsRollBackListingFavoritesAndNotifications() {
        tx.executeWithoutResult(status -> {
            wallets.debit(buyer1.getId(), new BigDecimal("150.00"));
            jdbc.update("insert into favorites (user_id, listing_id, created_at) values (?, ?, CURRENT_TIMESTAMP)",
                    buyer2.getId(), listingId);
        });
        assertEquals("INSUFFICIENT_FUNDS", execute(pending(buyer1, listingId)));
        assertEquals(Listings.Status.ACTIVE, listings.findById(listingId).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from favorites where listing_id = ?", Integer.class, listingId));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where listing_id = ?", Integer.class, listingId));
        assertTrue(mail.sent.isEmpty(), "Rollback must not send email");
        balance(buyer1, "0.00");
        balance(seller, "0.00");
    }

    @Test void legacyPurchasesStillCreateOnlyOneTransaction() throws Exception {
        List<Boolean> outcomes = race(() -> legacyPurchase(buyer1), () -> legacyPurchase(buyer2));
        assertEquals(1, Collections.frequency(outcomes, true));
        assertEquals(1, jdbc.queryForObject("select count(*) from transactions where listing_id = ?", Integer.class, listingId));
        assertEquals(Listings.Status.SOLD, listings.findById(listingId).orElseThrow().getStatus());
    }

    private boolean legacyPurchase(User buyer) {
        MyUserDetails details = new MyUserDetails(buyer);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities()));
        try {
            legacyPurchases.purchaseListing(listingId);
            return true;
        } catch (InformationExistException expected) {
            return false;   // clean rejection, no raw constraint error
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test void favoriteRacingTheSaleNeverSurvives() throws Exception {
        Long paymentId = pending(buyer1, listingId);
        List<String> outcomes = race(
            () -> {
                MyUserDetails details = new MyUserDetails(buyer2);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()));
                try {
                    // Wins the listing lock: commits while ACTIVE, sale cleanup removes it afterwards.
                    // Loses the lock: waits, then is rejected because the listing is SOLD.
                    favorites.addFavorite(listingId);
                    return "FAVORITED";
                } catch (InformationExistException expected) {
                    return "REJECTED";
                } finally {
                    SecurityContextHolder.clearContext();
                }
            },
            () -> execute(paymentId));

        // Either order ends with no favorite left on the sold listing
        assertEquals(0, jdbc.queryForObject("select count(*) from favorites where listing_id = ?", Integer.class, listingId),
                "A favorite must not survive a concurrent sale");
        assertEquals(Listings.Status.SOLD, listings.findById(listingId).orElseThrow().getStatus());
        // The sale always wins the listing claim, in both interleavings
        assertEquals("COMPLETED", outcomes.get(1));
    }

    private void assertSingleSale() {
        assertEquals(Listings.Status.SOLD, listings.findById(listingId).orElseThrow().getStatus());
        BigDecimal a = wallets.findByUserId(buyer1.getId()).orElseThrow().getBalance();
        BigDecimal b = wallets.findByUserId(buyer2.getId()).orElseThrow().getBalance();
        assertEquals(0, a.add(b).compareTo(new BigDecimal("200.00")));
        assertTrue((a.compareTo(new BigDecimal("50.00")) == 0 && b.compareTo(new BigDecimal("150.00")) == 0)
                || (b.compareTo(new BigDecimal("50.00")) == 0 && a.compareTo(new BigDecimal("150.00")) == 0));
        balance(seller, "100.00");
        assertEquals(1, jdbc.queryForObject("select count(*) from transactions where listing_id = ?", Integer.class, listingId));
    }
    private void balance(User user, String expected) {
        assertEquals(0, wallets.findByUserId(user.getId()).orElseThrow().getBalance().compareTo(new BigDecimal(expected)));
    }

    @AfterEach void cleanup() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (paymentExecutor.getActiveCount() > 0 || !paymentExecutor.getQueue().isEmpty()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Workers still running; fixtures retained for inspection");
            Thread.sleep(50);
        }
        tx.executeWithoutResult(status -> {
            for (Long id : listingIds) {
                jdbc.update("delete from notifications where listing_id = ?", id);
                jdbc.update("delete from favorites where listing_id = ?", id);
                jdbc.update("delete from transactions where listing_id = ?", id);
                jdbc.update("delete from payments where listing_id = ?", id);
                jdbc.update("delete from listings where id = ?", id);
            }
            for (Long id : userIds) {
                jdbc.update("delete from user_wallets where user_id = ?", id);
                jdbc.update("delete from audit_logs where actor_id = ?", id);
                users.deleteById(id);
            }
        });
    }
}
