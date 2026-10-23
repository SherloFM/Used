package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.RateLimitException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Payment;
import com.example.Used.Model.User;
import com.example.Used.Model.Wallet;
import com.example.Used.Model.Responses.PaymentResult;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.PaymentRepository;
import com.example.Used.Repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Accepts a purchase, records it as PENDING and hands the risky work to the
 * payment thread pool. This class NEVER locks rows or moves money itself —
 * it must stay fast and never block the HTTP request thread.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<Payment.Status> ACTIVE =
            List.of(Payment.Status.PENDING, Payment.Status.PROCESSING);

    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final WalletRepository walletRepository;
    private final CurrentUserService currentUserService;
    private final Executor paymentExecutor;   // the pool from Step 3
    private final PaymentWorker paymentWorker;

    public PaymentService(PaymentRepository paymentRepository,
                          ListingRepository listingRepository,
                          WalletRepository walletRepository,
                          CurrentUserService currentUserService,
                          @Qualifier("paymentExecutor") Executor paymentExecutor,
                          PaymentWorker paymentWorker) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletRepository = walletRepository;
        this.currentUserService = currentUserService;
        this.paymentExecutor = paymentExecutor;
        this.paymentWorker = paymentWorker;
    }

    public PaymentResult submitPayment(Long listingId) {
        User buyer = currentUserService.getCurrentUser();

        if (buyer.getRole() == User.Role.ADMIN) {
            throw new InformationExistException("Admins cannot buy listings");
        }

        Listings listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getUser().getId().equals(buyer.getId())) {
            throw new InformationExistException("You cannot buy your own listing");
        }
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException("Listing is no longer available for purchase");
        }

        // Idempotency: a double-click or retried request gets the SAME payment back
        Optional<Payment> active =
                paymentRepository.findFirstByBuyerIdAndListingIdAndStatusInOrderByIdDesc(
                        buyer.getId(), listingId, ACTIVE);
        if (active.isPresent()) {
            return PaymentResult.duplicate(active.get());
        }

        User seller = listing.getUser();

        // Wallets must exist before the worker runs (creating them inside two
        // racing workers could hit the unique user_id constraint)
        ensureWallet(buyer);
        ensureWallet(seller);

        Payment payment = new Payment();
        payment.setListing(listing);
        payment.setBuyer(buyer);
        payment.setSeller(seller);
        payment.setAmount(java.math.BigDecimal.valueOf(listing.getPrice())); // price is legacy double
        payment.setStatus(Payment.Status.PENDING);
        Payment saved = paymentRepository.save(payment); // unique index backstops races

        try {
            paymentExecutor.execute(() -> {
                try {
                    paymentWorker.executePayment(saved.getId());
                } catch (PaymentWorker.PaymentFailedException e) {
                    log.info("[PAYMENT] #{} failed: {}", saved.getId(), e.getReason());
                    paymentWorker.markFailed(saved.getId(), e.getReason());
                    return;
                } catch (Exception e) {
                    log.error("[PAYMENT] #{} system error", saved.getId(), e);
                    paymentWorker.markFailed(saved.getId(), "SYSTEM_ERROR");
                    return;
                }
                // Payment committed. Side effects must never be able to mark
                // a COMPLETED payment as FAILED, so they get their own try/catch.
                try {
                    paymentWorker.onPaymentCompleted(saved.getId());
                } catch (Exception e) {
                    log.error("[PAYMENT] #{} completed, but post-commit side effects failed", saved.getId(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            // Queue full — the pool is protecting the database
            saved.setStatus(Payment.Status.FAILED);
            saved.setFailureReason("QUEUE_FULL");
            paymentRepository.save(saved);
            throw new RateLimitException("Marketplace is busy processing payments - please try again");
        }

        log.info("[PAYMENT] #{} accepted (listing={}, buyer={})",
                saved.getId(), listingId, buyer.getId());
        return PaymentResult.accepted(saved);
    }

    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private void ensureWallet(User user) {
        if (walletRepository.findByUserId(user.getId()).isEmpty()) {
            walletRepository.save(new Wallet(user));
        }
    }
}
