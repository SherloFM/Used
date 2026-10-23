package com.example.Used.Service;

import com.example.Used.Model.*;
import com.example.Used.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final NotificationService notificationService;

    public PaymentWorker(PaymentRepository paymentRepository,
                         ListingRepository listingRepository,
                         WalletRepository walletRepository,
                         TransactionsRepository transactionsRepository,
                         UserRepository userRepository,
                         AuditLogService auditLogService,
                         EmailServices emailServices,
                         NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletRepository = walletRepository;
        this.transactionsRepository = transactionsRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.emailServices = emailServices;
        this.notificationService = notificationService;
    }

    /** All the money work in ONE transaction. Throws => everything rolls back. */
    @Transactional
    public void executePayment(Long paymentId) {
        log.info("[PAYMENT] #{} running on '{}'", paymentId, Thread.currentThread().getName());

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentFailedException("PAYMENT_NOT_FOUND"));

        payment.setStatus(Payment.Status.PROCESSING);
        paymentRepository.save(payment);

        // Defense 1: only one buyer wins the ACTIVE -> SOLD flip
        int claimed = listingRepository.updateStatusIfCurrent(
                payment.getListing().getId(), Listings.Status.ACTIVE, Listings.Status.SOLD);
        if (claimed == 0) {
            throw new PaymentFailedException("ALREADY_SOLD");
        }

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

        payment.setStatus(Payment.Status.COMPLETED);
        payment.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(payment);
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

            notificationService.sendToUser(payment.getSeller().getId(), "PAYMENT_COMPLETED", Map.of(
                    "paymentId", payment.getId(),
                    "listingId", payment.getListing().getId(),
                    "amount", payment.getAmount().toPlainString(),
                    "message", "Your item '" + title + "' was sold!"));
        });
    }

    /** Own transaction, started AFTER the payment rolled back, so the failure is kept. */
    @Transactional
    public void markFailed(Long paymentId, String reason) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailureReason(reason);
            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        });
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
