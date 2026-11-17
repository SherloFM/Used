package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.UserSummary;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.messaging.payment.PaymentRequested;
import com.example.marketplace.messaging.payment.PaymentRequestPublisher;
import com.example.marketplace.exceptions.ResourceNotFoundException;
import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Payment;
import com.example.marketplace.model.responses.CodRequestStatus;
import com.example.marketplace.model.responses.PaymentResult;
import com.example.marketplace.model.responses.PaymentStatus;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.PaymentRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import com.example.marketplace.client.EmailClient;
import com.example.marketplace.client.NotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<Payment.Status> ACTIVE =
            List.of(Payment.Status.PENDING, Payment.Status.PROCESSING);

    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final WalletService walletService;
    private final CurrentActorProvider currentActorProvider;
    private final AccountsClient accountsClient;
    private final PaymentRequestPublisher paymentRequestPublisher;
    private final PaymentWorker paymentWorker;
    private final EmailClient emailClient;
    private final NotificationClient notificationClient;

    public PaymentService(PaymentRepository paymentRepository,
                          ListingRepository listingRepository,
                          WalletService walletService,
                          CurrentActorProvider currentActorProvider,
                          AccountsClient accountsClient,
                          PaymentRequestPublisher paymentRequestPublisher,
                          PaymentWorker paymentWorker,
                          EmailClient emailClient,
                          NotificationClient notificationClient) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletService = walletService;
        this.currentActorProvider = currentActorProvider;
        this.accountsClient = accountsClient;
        this.paymentRequestPublisher = paymentRequestPublisher;
        this.paymentWorker = paymentWorker;
        this.emailClient = emailClient;
        this.notificationClient = notificationClient;
    }

    public PaymentResult submitPayment(Long listingId, Payment.Method method) {
        Payment.Method chosen = (method == null) ? Payment.Method.WALLET : method;

        CurrentActor buyer = currentActorProvider.get();

        if (buyer.isAdmin()) {
            throw new InformationExistException("Admins cannot buy listings");
        }
        if (chosen == Payment.Method.CARD) {
            throw new InformationExistException(
                    "Card payments are coming soon. Please use your wallet or cash on delivery.");
        }

        Listings listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getUserId().equals(buyer.id())) {
            throw new InformationExistException("You cannot buy your own listing");
        }
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException("Listing is no longer available for purchase");
        }

        // Idempotency: a double-click gets the SAME payment back
        Optional<Payment> sameBuyerActive =
                paymentRepository.findFirstByBuyerIdAndListingIdAndStatusInOrderByIdDesc(
                        buyer.id(), listingId, ACTIVE);
        if (sameBuyerActive.isPresent()) {
            Payment existing = sameBuyerActive.get();
            int pos = positionOf(existing);
            return PaymentResult.duplicate(existing.getId(), pos);
        }

        // For non-COD (wallet) payments, block if any active payment exists.
        // For COD, allow queuing: multiple PENDING COD payments per listing.
        Optional<Payment> anyActive =
                paymentRepository.findFirstByListingIdAndStatusInOrderByIdDesc(listingId, ACTIVE);
        if (anyActive.isPresent()) {
            Payment existing = anyActive.get();
            if (existing.getMethod() != Payment.Method.COD) {
                throw new InformationExistException("This item is already being purchased by another buyer");
            }
            if (existing.getBuyerId().equals(buyer.id())) {
                return PaymentResult.duplicate(existing.getId());
            }
            // COD queue: allow this buyer to join the queue
        }

        Long sellerId = listing.getUserId();

        if (chosen == Payment.Method.WALLET) {
            walletService.ensureWallet(buyer.id());
            walletService.ensureWallet(sellerId);
        }

        Payment payment = new Payment();
        payment.setListing(listing);
        payment.setBuyerId(buyer.id());
        payment.setSellerId(sellerId);
        payment.setMethod(chosen);
        payment.setAmount(BigDecimal.valueOf(listing.getPrice()));
        payment.setStatus(Payment.Status.PENDING);
        if (chosen == Payment.Method.COD) {
            payment.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        }

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            throw new InformationExistException("This item is already being purchased by another buyer");
        }

        if (chosen == Payment.Method.COD) {
            notifyCodRequest(saved);
            int pos = positionOf(saved);
            log.info("[COD] #{} request (listing={}, buyer={}, seller={}, queuePos={})",
                    saved.getId(), listingId, buyer.id(), sellerId, pos);
            return PaymentResult.accepted(saved.getId(), pos);
        }

        paymentRequestPublisher.publishAfterCommit(new PaymentRequested(1, saved.getId(), listingId));

        log.info("[PAYMENT] #{} accepted (listing={}, buyer={})",
                saved.getId(), listingId, buyer.id());
        return PaymentResult.accepted(saved.getId(), 1);
    }

    private int positionOf(Payment p) {
        if (p.getMethod() != Payment.Method.COD) return 1;
        return (int) paymentRepository.countByListingIdAndMethodAndStatusAndIdLessThanEqual(
                p.getListing().getId(), Payment.Method.COD, Payment.Status.PENDING, p.getId());
    }

    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public PaymentStatus getPaymentStatus(Long id) {
        Payment p = getPayment(id);
        int pos = positionOf(p);
        return PaymentStatus.from(p, pos);
    }

    @Transactional
    public PaymentStatus acceptCod(Long paymentId) {
        CurrentActor seller = currentActorProvider.get();
        requireCodPaymentForSeller(paymentId, seller.id());

        Payment payment = getPayment(paymentId);
        // only the first queued (lowest id) can be accepted
        int pos = positionOf(payment);
        if (pos > 1) {
            throw new InformationExistException("This request is not next in queue");
        }

        try {
            paymentWorker.acceptCodPayment(paymentId, seller.id());
        } catch (PaymentWorker.PaymentFailedException e) {
            handleCodFailure(paymentId, e.getReason());
            throw new InformationExistException(codErrorMessage(e.getReason()));
        }

        // mark other queued COD payments for this listing as ALREADY_SOLD
        failOtherQueued(payment);

        notifyCodDecision(paymentId, true);
        return PaymentStatus.from(getPayment(paymentId), 1);
    }

    @Transactional
    public PaymentStatus declineCod(Long paymentId) {
        CurrentActor seller = currentActorProvider.get();
        requireCodPaymentForSeller(paymentId, seller.id());

        try {
            paymentWorker.declineCodPayment(paymentId, seller.id());
        } catch (PaymentWorker.PaymentFailedException e) {
            handleCodFailure(paymentId, e.getReason());
            throw new InformationExistException(codErrorMessage(e.getReason()));
        }

        notifyCodDecision(paymentId, false);
        // promote next in queue
        promoteNext(paymentId);
        return PaymentStatus.from(getPayment(paymentId), 1);
    }

    public List<CodRequestStatus> getPendingCodRequests() {
        CurrentActor seller = currentActorProvider.get();
        return paymentRepository
                .findBySellerIdAndMethodAndStatusOrderByCreatedAtDesc(
                        seller.id(), Payment.Method.COD, Payment.Status.PENDING)
                .stream()
                .map(p -> CodRequestStatus.from(p, buyerName(p.getBuyerId()), positionOf(p)))
                .toList();
    }

    private void failOtherQueued(Payment accepted) {
        List<Payment> others = paymentRepository.findBySellerIdAndMethodAndStatusOrderByCreatedAtDesc(
                accepted.getSellerId(), Payment.Method.COD, Payment.Status.PENDING);
        for (Payment other : others) {
            if (other.getId().equals(accepted.getId())) continue;
            if (!other.getListing().getId().equals(accepted.getListing().getId())) continue;
            paymentWorker.markFailed(other.getId(), "ALREADY_SOLD");
            Long otherListingId = other.getListing().getId();
            notificationClient.sendToUser(other.getBuyerId(), "COD_DECISION", Map.of(
                    "title", "Request declined",
                    "message", "Your cash-on-delivery request was declined because the item was sold to another buyer.",
                    "type", "error",
                    "paymentId", other.getId(),
                    "listingId", otherListingId,
                    "status", "FAILED",
                    "accepted", false));
        }
    }

    private void promoteNext(Long declinedPaymentId) {
        Payment declined = getPayment(declinedPaymentId);
        // find next pending COD for same listing
        List<Payment> pending = paymentRepository.findBySellerIdAndMethodAndStatusOrderByCreatedAtDesc(
                declined.getSellerId(), Payment.Method.COD, Payment.Status.PENDING);
        for (Payment next : pending) {
            if (!next.getListing().getId().equals(declined.getListing().getId())) continue;
            // notify seller of next request
            notifyCodRequest(next);
            break;
        }
    }

    private void requireCodPaymentForSeller(Long paymentId, Long sellerId) {
        Payment payment = getPayment(paymentId);
        if (!payment.getSellerId().equals(sellerId)) {
            throw new ResourceNotFoundException("Payment not found");
        }
        if (payment.getMethod() != Payment.Method.COD) {
            throw new InformationExistException("This payment is not a cash-on-delivery request");
        }
    }

    private void handleCodFailure(Long paymentId, String reason) {
        if (!"ALREADY_HANDLED".equals(reason) && !"PAYMENT_NOT_FOUND".equals(reason)) {
            paymentWorker.markFailed(paymentId, reason);
        }
    }

    private String codErrorMessage(String reason) {
        return switch (reason) {
            case "ALREADY_HANDLED" -> "This request has already been handled";
            case "ALREADY_SOLD" -> "This item has already been sold";
            case "PAYMENT_NOT_FOUND" -> "Payment not found";
            case "COD_EXPIRED" -> "This request has expired";
            default -> "Could not update this request. Please try again.";
        };
    }

    private void notifyCodRequest(Payment payment) {
        Listings listing = payment.getListing();
        String title = (listing != null) ? listing.getTitle() : "listing";
        String buyerName = buyerName(payment.getBuyerId());

        UserSummary seller = accountsClient.find(payment.getSellerId()).orElse(null);
        String sellerEmail = seller != null ? seller.email() : null;

        try {
            if (sellerEmail != null) {
                emailClient.sendCodRequestEmail(
                        sellerEmail,
                        title,
                        buyerName,
                        payment.getAmount());
            }
        } catch (Exception e) {
            log.error("[COD] request email failed for payment #{}", payment.getId(), e);
        }

        try {
            notificationClient.sendToUser(payment.getSellerId(), "COD_REQUEST", Map.of(
                    "title", "New cash-on-delivery request",
                    "message", buyerName + " wants to buy '" + title + "' via cash on delivery.",
                    "type", "info",
                    "paymentId", payment.getId(),
                    "listingId", listing != null ? listing.getId() : 0L,
                    "listingTitle", title,
                    "buyerName", buyerName,
                    "amount", payment.getAmount().toPlainString(),
                    "imgUrl", (listing != null && listing.getImgUrl() != null) ? listing.getImgUrl() : ""));
        } catch (Exception e) {
            log.error("[COD] notification failed for payment #{}", payment.getId(), e);
        }
    }

    private void notifyCodDecision(Long paymentId, boolean accepted) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            Listings listing = payment.getListing();
            String title = (listing != null) ? listing.getTitle() : "listing";
            String message = accepted
                    ? "Your cash-on-delivery request for '" + title + "' was accepted."
                    : "Your cash-on-delivery request for '" + title + "' was declined.";

            UserSummary buyer = accountsClient.find(payment.getBuyerId()).orElse(null);
            String buyerEmail = buyer != null ? buyer.email() : null;

            try {
                if (buyerEmail != null) {
                    if (accepted) {
                        emailClient.sendCodAcceptedEmail(buyerEmail, title);
                    } else {
                        emailClient.sendCodDeclinedEmail(buyerEmail, title);
                    }
                }
            } catch (Exception e) {
                log.error("[COD] decision email failed for payment #{}", paymentId, e);
            }

            try {
                notificationClient.sendToUser(payment.getBuyerId(), "COD_DECISION", Map.of(
                        "title", accepted ? "Request accepted" : "Request declined",
                        "message", message,
                        "type", accepted ? "success" : "error",
                        "paymentId", paymentId,
                        "listingId", listing != null ? listing.getId() : 0L,
                        "status", payment.getStatus().name(),
                        "accepted", accepted));
            } catch (Exception e) {
                log.error("[COD] decision notification failed for payment #{}", paymentId, e);
            }
        });
    }

    @Scheduled(fixedDelay = 30000)
    public void expireCodRequests() {
        List<Payment> expired = paymentRepository
                .findByMethodAndStatusAndExpiresAtBefore(
                        Payment.Method.COD, Payment.Status.PENDING, LocalDateTime.now());
        for (Payment p : expired) {
            try {
                paymentWorker.markFailed(p.getId(), "COD_EXPIRED");
            } catch (Exception e) {
                log.error("[COD] expiry markFailed failed for #{}", p.getId(), e);
            }
            try {
                notificationClient.sendToUser(p.getBuyerId(), "COD_EXPIRED", Map.of(
                        "title", "Request expired",
                        "message", "Your cash-on-delivery request for '" + p.getListing().getTitle() + "' expired (seller didn't respond in 5 minutes).",
                        "type", "warning",
                        "paymentId", p.getId(),
                        "listingId", p.getListing().getId()));
            } catch (Exception e) {
                log.error("[COD] expiry notification failed for #{}", p.getId(), e);
            }
            try {
                UserSummary buyer = accountsClient.find(p.getBuyerId()).orElse(null);
                if (buyer != null && buyer.email() != null) {
                    emailClient.sendCodDeclinedEmail(buyer.email(), p.getListing().getTitle());
                }
            } catch (Exception e) {
                log.error("[COD] expiry email failed for #{}", p.getId(), e);
            }
        }
    }

    /** Display name for a buyer id; never null so notification payloads stay valid. */
    private String buyerName(Long buyerId) {
        return accountsClient.find(buyerId)
                .map(user -> user.username() != null ? user.username()
                        : user.email() != null ? user.email() : "A buyer")
                .orElse("A buyer");
    }
}
