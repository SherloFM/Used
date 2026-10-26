package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.RateLimitException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Notification;
import com.example.Used.Model.Payment;
import com.example.Used.Model.User;
import com.example.Used.Model.Responses.CodRequestStatus;
import com.example.Used.Model.Responses.PaymentResult;
import com.example.Used.Model.Responses.PaymentStatus;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<Payment.Status> ACTIVE =
            List.of(Payment.Status.PENDING, Payment.Status.PROCESSING);

    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final WalletService walletService;
    private final CurrentUserService currentUserService;
    private final Executor paymentExecutor;
    private final PaymentWorker paymentWorker;
    private final EmailServices emailServices;
    private final NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository,
                          ListingRepository listingRepository,
                          WalletService walletService,
                          CurrentUserService currentUserService,
                          @Qualifier("paymentExecutor") Executor paymentExecutor,
                          PaymentWorker paymentWorker,
                          EmailServices emailServices,
                          NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletService = walletService;
        this.currentUserService = currentUserService;
        this.paymentExecutor = paymentExecutor;
        this.paymentWorker = paymentWorker;
        this.emailServices = emailServices;
        this.notificationService = notificationService;
    }

    public PaymentResult submitPayment(Long listingId, Payment.Method method) {
        Payment.Method chosen = (method == null) ? Payment.Method.WALLET : method;

        User buyer = currentUserService.getCurrentUser();

        if (buyer.getRole() == User.Role.ADMIN) {
            throw new InformationExistException("Admins cannot buy listings");
        }
        if (chosen == Payment.Method.CARD) {
            throw new InformationExistException(
                    "Card payments are coming soon. Please use your wallet or cash on delivery.");
        }

        Listings listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getUser().getId().equals(buyer.getId())) {
            throw new InformationExistException("You cannot buy your own listing");
        }
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException("Listing is no longer available for purchase");
        }

        // Idempotency: a double-click gets the SAME payment back
        Optional<Payment> sameBuyerActive =
                paymentRepository.findFirstByBuyerIdAndListingIdAndStatusInOrderByIdDesc(
                        buyer.getId(), listingId, ACTIVE);
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
            if (existing.getBuyer().getId().equals(buyer.getId())) {
                return PaymentResult.duplicate(existing.getId());
            }
            // COD queue: allow this buyer to join the queue
        }

        User seller = listing.getUser();

        if (chosen == Payment.Method.WALLET) {
            walletService.ensureWallet(buyer.getId());
            walletService.ensureWallet(seller.getId());
        }

        Payment payment = new Payment();
        payment.setListing(listing);
        payment.setBuyer(buyer);
        payment.setSeller(seller);
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
                    saved.getId(), listingId, buyer.getId(), seller.getId(), pos);
            return PaymentResult.accepted(saved.getId(), pos);
        }

        submitToPool(saved);

        log.info("[PAYMENT] #{} accepted (listing={}, buyer={})",
                saved.getId(), listingId, buyer.getId());
        return PaymentResult.accepted(saved.getId(), 1);
    }

    private int positionOf(Payment p) {
        if (p.getMethod() != Payment.Method.COD) return 1;
        return (int) paymentRepository.countByListingIdAndMethodAndStatusAndIdLessThanEqual(
                p.getListing().getId(), Payment.Method.COD, Payment.Status.PENDING, p.getId());
    }

    private void submitToPool(Payment saved) {
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
                try {
                    paymentWorker.onPaymentCompleted(saved.getId());
                } catch (Exception e) {
                    log.error("[PAYMENT] #{} completed, but post-commit side effects failed", saved.getId(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            paymentWorker.markFailed(saved.getId(), "QUEUE_FULL");
            throw new RateLimitException("Marketplace is busy processing payments - please try again");
        }
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
        User seller = currentUserService.getCurrentUser();
        requireCodPaymentForSeller(paymentId, seller);

        Payment payment = getPayment(paymentId);
        // only the first queued (lowest id) can be accepted
        int pos = positionOf(payment);
        if (pos > 1) {
            throw new InformationExistException("This request is not next in queue");
        }

        try {
            paymentWorker.acceptCodPayment(paymentId, seller.getId());
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
        User seller = currentUserService.getCurrentUser();
        requireCodPaymentForSeller(paymentId, seller);

        try {
            paymentWorker.declineCodPayment(paymentId, seller.getId());
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
        User seller = currentUserService.getCurrentUser();
        return paymentRepository
                .findBySellerIdAndMethodAndStatusOrderByCreatedAtDesc(
                        seller.getId(), Payment.Method.COD, Payment.Status.PENDING)
                .stream()
                .map(p -> CodRequestStatus.from(p, positionOf(p)))
                .toList();
    }

    private void failOtherQueued(Payment accepted) {
        List<Payment> others = paymentRepository.findBySellerIdAndMethodAndStatusOrderByCreatedAtDesc(
                accepted.getSeller().getId(), Payment.Method.COD, Payment.Status.PENDING);
        for (Payment other : others) {
            if (other.getId().equals(accepted.getId())) continue;
            if (!other.getListing().getId().equals(accepted.getListing().getId())) continue;
            paymentWorker.markFailed(other.getId(), "ALREADY_SOLD");
            Long otherListingId = other.getListing().getId();
            notificationService.sendToUser(other.getBuyer().getId(), "COD_DECISION", Map.of(
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
                declined.getSeller().getId(), Payment.Method.COD, Payment.Status.PENDING);
        for (Payment next : pending) {
            if (!next.getListing().getId().equals(declined.getListing().getId())) continue;
            // notify seller of next request
            notifyCodRequest(next);
            break;
        }
    }

    private void requireCodPaymentForSeller(Long paymentId, User seller) {
        Payment payment = getPayment(paymentId);
        if (!payment.getSeller().getId().equals(seller.getId())) {
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

        try {
            emailServices.sendCodRequestEmail(
                    payment.getSeller().getEmail(),
                    title,
                    payment.getBuyer().getUsername(),
                    payment.getAmount());
        } catch (Exception e) {
            log.error("[COD] request email failed for payment #{}", payment.getId(), e);
        }

        try {
            notificationService.sendToUser(payment.getSeller().getId(), "COD_REQUEST", Map.of(
                    "title", "New cash-on-delivery request",
                    "message", payment.getBuyer().getUsername() + " wants to buy '" + title + "' via cash on delivery.",
                    "type", "info",
                    "paymentId", payment.getId(),
                    "listingId", listing != null ? listing.getId() : 0L,
                    "listingTitle", title,
                    "buyerName", payment.getBuyer().getUsername(),
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

            try {
                if (accepted) {
                    emailServices.sendCodAcceptedEmail(payment.getBuyer().getEmail(), title);
                } else {
                    emailServices.sendCodDeclinedEmail(payment.getBuyer().getEmail(), title);
                }
            } catch (Exception e) {
                log.error("[COD] decision email failed for payment #{}", paymentId, e);
            }

            try {
                notificationService.sendToUser(payment.getBuyer().getId(), "COD_DECISION", Map.of(
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
                notificationService.sendToUser(p.getBuyer().getId(), "COD_EXPIRED", Map.of(
                        "title", "Request expired",
                        "message", "Your cash-on-delivery request for '" + p.getListing().getTitle() + "' expired (seller didn't respond in 5 minutes).",
                        "type", "warning",
                        "paymentId", p.getId(),
                        "listingId", p.getListing().getId()));
            } catch (Exception e) {
                log.error("[COD] expiry notification failed for #{}", p.getId(), e);
            }
            try {
                emailServices.sendCodDeclinedEmail(p.getBuyer().getEmail(), p.getListing().getTitle());
            } catch (Exception e) {
                log.error("[COD] expiry email failed for #{}", p.getId(), e);
            }
        }
    }
}
