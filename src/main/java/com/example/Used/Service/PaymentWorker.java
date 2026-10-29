package com.example.Used.Service;

import com.example.Used.Model.*;
import com.example.Used.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Executes ONE payment on a payment-worker thread.
 *
 * Rule that keeps @Transactional working here: every method below is called
 * from PaymentService (a different class), never from inside this class.
 * Self-invoked @Transactional methods would be silently ignored by Spring.
 */
@Service
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final WalletRepository walletRepository;
    private final TransactionsRepository transactionsRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final EmailServices emailServices;
    private final FavoriteService favoriteService;
    private final NotificationService notificationService;

    public PaymentWorker(PaymentRepository paymentRepository,
                         ListingRepository listingRepository,
                         WalletRepository walletRepository,
                         TransactionsRepository transactionsRepository,
                         UserRepository userRepository,
                         AuditLogService auditLogService,
                         EmailServices emailServices,
                         FavoriteService favoriteService,
                         NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletRepository = walletRepository;
        this.transactionsRepository = transactionsRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.emailServices = emailServices;
        this.favoriteService = favoriteService;
        this.notificationService = notificationService;
    }

    /** All the money work in ONE transaction. Throws => everything rolls back. */
    @Transactional
    public void executePayment(Long paymentId) {
        log.info("[PAYMENT] #{} running on '{}'", paymentId, Thread.currentThread().getName());

        // Claim before any work: exactly one execution can move PENDING ->
        // PROCESSING (same pattern as acceptCodPayment). A second run of the
        // same payment (retry, stale worker task) fails the claim and stops
        // here - no listing claim, no debits.
        if (paymentRepository.updateStatusIfCurrent(
                paymentId, Payment.Status.PENDING, Payment.Status.PROCESSING) == 0) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentFailedException("PAYMENT_NOT_FOUND"));

        // Defense 1: only one buyer wins the ACTIVE -> SOLD flip
        int claimed = listingRepository.updateStatusIfCurrent(
                payment.getListing().getId(), Listings.Status.ACTIVE, Listings.Status.SOLD);
        if (claimed == 0) {
            throw new PaymentFailedException("ALREADY_SOLD");
        }
        favoriteService.handleListingRemoved(payment.getListing().getId(), "sold");

        // Defense 3: lock both wallets, always lowest id first (no deadlocks)
        Long buyerId = payment.getBuyer().getId();
        Long sellerId = payment.getSeller().getId();
        if (buyerId <= sellerId) {
            walletRepository.findByIdForUpdate(buyerId);
            walletRepository.findByIdForUpdate(sellerId);
        } else {
            walletRepository.findByIdForUpdate(sellerId);
            walletRepository.findByIdForUpdate(buyerId);
        }

        // Defense 2: the balance check lives INSIDE the update
        if (walletRepository.debit(buyerId, payment.getAmount()) == 0) {
            throw new PaymentFailedException("INSUFFICIENT_FUNDS");
        }
        if (walletRepository.credit(sellerId, payment.getAmount()) == 0) {
            throw new PaymentFailedException("WALLET_MISSING");
        }

        Transactions transaction = new Transactions();
        transaction.setListing(payment.getListing());
        transaction.setBuyer(payment.getBuyer());
        transaction.setSeller(payment.getSeller());
        transaction.setAmount(payment.getAmount().doubleValue()); // legacy Transactions.amount is double
        transaction.setStatus(Transactions.Status.COMPLETED);
        transactionsRepository.save(transaction);

        // Conditional close: stamps processedAt and can never overwrite a row
        // some other path already failed or completed.
        if (paymentRepository.markCompletedIfCurrent(
                paymentId, Payment.Status.PROCESSING, Payment.Status.COMPLETED) == 0) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }
    }

    /** Runs only after executePayment committed: audit trail, email, live push. */
    public void onPaymentCompleted(Long paymentId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            User buyer = userRepository.findById(payment.getBuyer().getId()).orElse(null);
            User seller = userRepository.findById(payment.getSeller().getId()).orElse(null);
            Listings listing = listingRepository.findById(payment.getListing().getId()).orElse(null);
            String title = (listing != null) ? listing.getTitle() : "listing";

            auditLogService.log(
                    AuditLog.AuditAction.LISTING_SOLD,
                    buyer,
                    "Listing '" + title + "' (id=" + payment.getListing().getId()
                            + ") sold for " + payment.getAmount(),
                    payment.getSeller().getId());

            if (seller != null && listing != null) {
                emailServices.sendListingSoldEmail(seller.getEmail(), title);
            }

        });
    }

    /**
     * Seller accepted a cash-on-delivery request. No money moves: the listing
     * is claimed (ACTIVE -> SOLD) and a COMPLETED Transactions row is written,
     * inside ONE transaction. Two Accept clicks race on the status UPDATE and
     * the listing UPDATE, so only one can win.
     */
    @Transactional
    public void acceptCodPayment(Long paymentId, Long sellerId) {
        Payment payment = loadPendingCodForSeller(paymentId, sellerId);

        if (paymentRepository.updateStatusIfCurrent(
                paymentId, Payment.Status.PENDING, Payment.Status.PROCESSING) == 0) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }

        int claimed = listingRepository.updateStatusIfCurrent(
                payment.getListing().getId(), Listings.Status.ACTIVE, Listings.Status.SOLD);
        if (claimed == 0) {
            throw new PaymentFailedException("ALREADY_SOLD");
        }
        favoriteService.handleListingRemoved(payment.getListing().getId(), "sold");

        Transactions transaction = new Transactions();
        transaction.setListing(payment.getListing());
        transaction.setBuyer(payment.getBuyer());
        transaction.setSeller(payment.getSeller());
        transaction.setAmount(payment.getAmount().doubleValue()); // legacy Transactions.amount is double
        transaction.setStatus(Transactions.Status.COMPLETED);
        transactionsRepository.save(transaction);

        if (paymentRepository.markCompletedIfCurrent(
                paymentId, Payment.Status.PROCESSING, Payment.Status.COMPLETED) == 0) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }
        String title = payment.getListing().getTitle();
        notificationService.sendToUser(payment.getSeller().getId(), "LISTING_SOLD", Map.of(
                "title", "Item sold",
                "message", "Your item '" + title + "' was sold!",
                "type", "success",
                "paymentId", payment.getId(),
                "listingId", payment.getListing().getId()));
    }

    /** Seller declined: the payment is closed with reason COD_DECLINED. */
    @Transactional
    public void declineCodPayment(Long paymentId, Long sellerId) {
        loadPendingCodForSeller(paymentId, sellerId);

        int updated = paymentRepository.failIfActive(
                paymentId,
                "COD_DECLINED",
                Payment.Status.FAILED,
                List.of(Payment.Status.PENDING, Payment.Status.PROCESSING));

        if (updated == 0) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }
    }

    private Payment loadPendingCodForSeller(Long paymentId, Long sellerId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentFailedException("PAYMENT_NOT_FOUND"));

        if (payment.getMethod() != Payment.Method.COD
                || payment.getStatus() != Payment.Status.PENDING
                || !payment.getSeller().getId().equals(sellerId)) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }
        return payment;
    }

    /** Own transaction, started AFTER the payment rolled back, so the failure is kept. */
    @Transactional
    public void markFailed(Long paymentId, String reason) {
        // Conditional UPDATE: a late failure can never overwrite COMPLETED.
        paymentRepository.failIfActive(
                paymentId,
                reason,
                Payment.Status.FAILED,
                List.of(Payment.Status.PENDING, Payment.Status.PROCESSING));
    }

    /** Carries the failure reason: ALREADY_SOLD, INSUFFICIENT_FUNDS, ... */
    public static class PaymentFailedException extends RuntimeException {
        private final String reason;

        public PaymentFailedException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
