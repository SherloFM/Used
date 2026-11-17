package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.AuditClient;
import com.example.marketplace.client.UserSummary;
import com.example.marketplace.messaging.ListingEvent;
import com.example.marketplace.messaging.ListingLifecycle;
import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Payment;
import com.example.marketplace.model.Transactions;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.PaymentRepository;
import com.example.marketplace.repository.TransactionsRepository;
import com.example.marketplace.repository.WalletRepository;

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
     * from another bean (PaymentService or PaymentJobRunner), never from inside this class.
 * Self-invoked @Transactional methods would be silently ignored by Spring.
 */
@Service
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final WalletRepository walletRepository;
    private final TransactionsRepository transactionsRepository;
    private final AccountsClient accountsClient;
    private final AuditClient auditClient;
    private final ListingLifecycle listingLifecycle;

    public PaymentWorker(PaymentRepository paymentRepository,
                         ListingRepository listingRepository,
                         WalletRepository walletRepository,
                         TransactionsRepository transactionsRepository,
                         AccountsClient accountsClient,
                         AuditClient auditClient,
                         ListingLifecycle listingLifecycle) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletRepository = walletRepository;
        this.transactionsRepository = transactionsRepository;
        this.accountsClient = accountsClient;
        this.auditClient = auditClient;
        this.listingLifecycle = listingLifecycle;
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

        // The bulk status update cleared the persistence context, so re-read:
        // the event must carry a SOLD row, not a detached ACTIVE snapshot.
        Listings sold = listingRepository.findById(payment.getListing().getId())
                .orElseThrow(() -> new PaymentFailedException("LISTING_NOT_FOUND"));
        listingLifecycle.sold(sold, ListingEvent.Source.PAYMENT);

        // Defense 3: lock both wallets, always lowest id first (no deadlocks)
        Long buyerId = payment.getBuyerId();
        Long sellerId = payment.getSellerId();
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
        transaction.setBuyerId(buyerId);
        transaction.setSellerId(sellerId);
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

    /** Runs only after executePayment committed: the audit trail. */
    public void onPaymentCompleted(Long paymentId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            UserSummary buyer = accountsClient.find(payment.getBuyerId()).orElse(null);
            Listings listing = listingRepository.findById(payment.getListing().getId()).orElse(null);
            String title = (listing != null) ? listing.getTitle() : "listing";

            auditClient.log(
                    "LISTING_SOLD",
                    payment.getBuyerId(),
                    buyer != null ? buyer.username() : null,
                    "Listing '" + title + "' (id=" + payment.getListing().getId()
                            + ") sold for " + payment.getAmount(),
                    payment.getSellerId());

            // The seller email and in-app push are sent by Notification from the
            // listing.sold event, after the sale committed.
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

        Listings sold = listingRepository.findById(payment.getListing().getId())
                .orElseThrow(() -> new PaymentFailedException("LISTING_NOT_FOUND"));
        listingLifecycle.sold(sold, ListingEvent.Source.COD);

        Transactions transaction = new Transactions();
        transaction.setListing(payment.getListing());
        transaction.setBuyerId(payment.getBuyerId());
        transaction.setSellerId(payment.getSellerId());
        transaction.setAmount(payment.getAmount().doubleValue()); // legacy Transactions.amount is double
        transaction.setStatus(Transactions.Status.COMPLETED);
        transactionsRepository.save(transaction);

        if (paymentRepository.markCompletedIfCurrent(
                paymentId, Payment.Status.PROCESSING, Payment.Status.COMPLETED) == 0) {
            throw new PaymentFailedException("ALREADY_HANDLED");
        }
        // The seller's in-app push is sent by Notification from the listing.sold
        // event, AFTER this transaction commits. Sending it here could announce
        // a sale that then rolled back.
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
                || !payment.getSellerId().equals(sellerId)) {
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

    /** Called only after confirmed DLT delivery; never changes COD or a completed payment. */
    @Transactional
    public void markDispatchFailed(Long paymentId, Long listingId) {
        paymentRepository.failPendingDispatch(paymentId, listingId,
                Payment.Status.PENDING, Payment.Status.FAILED, Payment.Method.WALLET);
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
