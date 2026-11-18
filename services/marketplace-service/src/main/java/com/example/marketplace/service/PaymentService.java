package com.example.marketplace.service;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.CheckoutSession;
import com.example.marketplace.client.GatewayOrderReference;
import com.example.marketplace.client.UserSummary;
import com.example.marketplace.client.WalletGatewayClient;
import com.example.marketplace.exceptions.GatewaySignatureException;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.messaging.payment.PaymentRequested;
import com.example.marketplace.messaging.payment.PaymentRequestPublisher;
import com.example.marketplace.exceptions.ResourceNotFoundException;
import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Payment;
import com.example.marketplace.model.responses.CodRequestStatus;
import com.example.marketplace.model.responses.GatewayCheckoutResponse;
import com.example.marketplace.model.responses.PaymentResult;
import com.example.marketplace.model.responses.PaymentStatus;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.PaymentRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import com.example.marketplace.client.EmailClient;
import com.example.marketplace.client.NotificationClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<Payment.Status> ACTIVE =
            List.of(Payment.Status.PENDING, Payment.Status.PROCESSING);

    /** Gateway limits: BHD 0.100 - 5000.000, at most three decimals. */
    private static final BigDecimal GATEWAY_MIN = new BigDecimal("0.100");
    private static final BigDecimal GATEWAY_MAX = new BigDecimal("5000.000");
    private static final int GATEWAY_SESSION_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final WalletService walletService;
    private final CurrentActorProvider currentActorProvider;
    private final AccountsClient accountsClient;
    private final PaymentRequestPublisher paymentRequestPublisher;
    private final PaymentWorker paymentWorker;
    private final EmailClient emailClient;
    private final NotificationClient notificationClient;
    private final WalletGatewayClient walletGateway;
    private final ObjectMapper objectMapper;
    private final String gatewayPublicBaseUrl;

    public PaymentService(PaymentRepository paymentRepository,
                          ListingRepository listingRepository,
                          WalletService walletService,
                          CurrentActorProvider currentActorProvider,
                          AccountsClient accountsClient,
                          PaymentRequestPublisher paymentRequestPublisher,
                          PaymentWorker paymentWorker,
                          EmailClient emailClient,
                          NotificationClient notificationClient,
                          WalletGatewayClient walletGateway,
                          ObjectMapper objectMapper,
                          @Value("${wallet.gateway.public-base-url:http://localhost:8080}")
                          String gatewayPublicBaseUrl) {
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
        this.walletService = walletService;
        this.currentActorProvider = currentActorProvider;
        this.accountsClient = accountsClient;
        this.paymentRequestPublisher = paymentRequestPublisher;
        this.paymentWorker = paymentWorker;
        this.emailClient = emailClient;
        this.notificationClient = notificationClient;
        this.walletGateway = walletGateway;
        this.objectMapper = objectMapper;
        this.gatewayPublicBaseUrl = gatewayPublicBaseUrl == null ? "" : gatewayPublicBaseUrl.replaceAll("/+$", "");
    }


    public PaymentResult submitPayment(Long listingId, Payment.Method method) {
        Payment.Method chosen = (method == null) ? Payment.Method.WALLET : method;

        CurrentActor buyer = currentActorProvider.get();

        if (buyer.isAdmin()) {
            throw new InformationExistException("Admins cannot buy listings");
        }
        if (chosen == Payment.Method.CARD) {
            throw new InformationExistException(
                    "Card details are not collected here. Please pay through the Almotawaj Wallet checkout.");
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

    // ==========================================================
    // ALMOTAWAI WALLET GATEWAY
    // The buyer pays on the gateway's checkout page. The gateway holds the
    // money; Marketplace only credits the seller once a PAID status is
    // verified, either from the signed callback or from a status lookup.
    // No schema change: Payment.Method.CARD (the existing enum value) and
    // the payment id inside the order reference are enough.
    // ==========================================================

    /** Creates the payment row and the gateway checkout session. */
    public GatewayCheckoutResponse startGatewayPayment(Long listingId) {
        CurrentActor buyer = currentActorProvider.get();

        if (buyer.isAdmin()) {
            throw new InformationExistException("Admins cannot buy listings");
        }
        if (!walletGateway.isConfigured()) {
            throw new InformationExistException(
                    "Almotawaj Wallet payments are not available yet. "
                            + "Please use your wallet balance or cash on delivery.");
        }

        Listings listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getUserId().equals(buyer.id())) {
            throw new InformationExistException("You cannot buy your own listing");
        }
        if (listing.getStatus() != Listings.Status.ACTIVE) {
            throw new InformationExistException("Listing is no longer available for purchase");
        }

        BigDecimal amount = BigDecimal.valueOf(listing.getPrice()).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(GATEWAY_MIN) < 0 || amount.compareTo(GATEWAY_MAX) > 0) {
            throw new InformationExistException(
                    "Almotawaj Wallet accepts orders from 0.100 to 5000.000. This item is outside that range.");
        }

        // Abandoned sessions must not block the item for the buyer or anyone else.
        expireStaleGatewayPayments();

        // A new attempt supersedes this buyer's own unfinished gateway payment.
        paymentRepository.findFirstByBuyerIdAndListingIdAndStatusInOrderByIdDesc(
                        buyer.id(), listingId, ACTIVE)
                .filter(existing -> existing.getMethod() == Payment.Method.CARD)
                .ifPresent(existing -> paymentWorker.markFailed(existing.getId(), "GATEWAY_SUPERSEDED"));

        Optional<Payment> anyActive =
                paymentRepository.findFirstByListingIdAndStatusInOrderByIdDesc(listingId, ACTIVE);
        if (anyActive.isPresent()) {
            Payment existing = anyActive.get();
            if (!existing.getBuyerId().equals(buyer.id())
                    && existing.getMethod() != Payment.Method.COD) {
                throw new InformationExistException(
                        "This item is already being purchased by another buyer");
            }
        }

        walletService.ensureWallet(listing.getUserId());

        Payment payment = new Payment();
        payment.setListing(listing);
        payment.setBuyerId(buyer.id());
        payment.setSellerId(listing.getUserId());
        // Existing enum value: the column stores CARD, the buyer sees "Almotawaj Wallet".
        payment.setMethod(Payment.Method.CARD);
        payment.setAmount(amount);
        payment.setStatus(Payment.Status.PENDING);
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(GATEWAY_SESSION_MINUTES));
        Payment saved = paymentRepository.save(payment);

        String returnUrl = gatewayPublicBaseUrl + "/payment/" + listingId;
        try {
            CheckoutSession session = walletGateway.create(
                    GatewayOrderReference.create(saved.getId()),
                    amount,
                    gatewayDescription(listing),
                    returnUrl,
                    GATEWAY_SESSION_MINUTES);
            log.info("[WALLET] #{} checkout session {} created (listing={}, buyer={})",
                    saved.getId(), session.id(), listingId, buyer.id());
            return GatewayCheckoutResponse.from(saved.getId(), session);
        } catch (WalletGatewayClient.GatewayException failure) {
            paymentWorker.markFailed(saved.getId(), "GATEWAY_" + failure.getCode());
            log.warn("[WALLET] checkout session failed for payment #{}: {}",
                    saved.getId(), failure.getCode());
            throw new InformationExistException(gatewayErrorMessage(failure.getCode()));
        }
    }

    /** Confirms a returned checkout and completes the sale when the gateway says PAID. */
    public PaymentStatus confirmGatewayPayment(Long paymentId, String sessionId) {
        CurrentActor buyer = currentActorProvider.get();
        Payment payment = getPayment(paymentId);

        if (!payment.getBuyerId().equals(buyer.id())) {
            throw new ResourceNotFoundException("Payment not found");
        }
        if (payment.getMethod() != Payment.Method.CARD) {
            throw new InformationExistException("This is not an Almotawaj Wallet payment");
        }
        if (payment.getStatus() == Payment.Status.COMPLETED) {
            return getPaymentStatus(paymentId);
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new InformationExistException("The wallet checkout reference is missing.");
        }

        CheckoutSession session = fetchSession(sessionId);
        Long referencedPayment = GatewayOrderReference.paymentId(session.orderReference());
        if (!paymentId.equals(referencedPayment)) {
            throw new InformationExistException(
                    "That wallet checkout does not belong to this order.");
        }
        if (session.amount() == null || session.amount().compareTo(payment.getAmount()) != 0) {
            throw new InformationExistException(
                    "The wallet checkout amount does not match this order.");
        }

        switch (session.status()) {
            case CheckoutSession.PAID -> completeGatewayPayment(paymentId, sessionId);
            case CheckoutSession.CANCELLED -> paymentWorker.markFailed(paymentId, "GATEWAY_CANCELLED");
            case CheckoutSession.EXPIRED -> paymentWorker.markFailed(paymentId, "GATEWAY_EXPIRED");
            case CheckoutSession.REFUNDED -> paymentWorker.markFailed(paymentId, "GATEWAY_REFUNDED");
            default -> { /* still PENDING: the page keeps polling */ }
        }
        return getPaymentStatus(paymentId);
    }

    /**
     * Signed gateway callback. Safe to repeat: completion is idempotent, and a
     * payment that arrives after the local expiry is still honoured because the
     * presentation of a paid session means the money was taken.
     */
    public void handleGatewayCallback(String rawBody, String signature) {
        if (!walletGateway.canVerifyCallbacks()) {
            log.error("[WALLET] Callback rejected: WALLET_GATEWAY_SIGNING_SECRET is not configured");
            throw new GatewaySignatureException();
        }
        if (!walletGateway.validSignature(rawBody, signature)) {
            throw new GatewaySignatureException();
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception failure) {
            throw new InformationExistException("Malformed wallet callback.");
        }

        if (!"checkout.paid".equals(event.path("event").asText())
                || !CheckoutSession.PAID.equals(event.path("status").asText())) {
            return; // cancelled / expired / refunded events need no fulfilment
        }

        Long paymentId = GatewayOrderReference.paymentId(event.path("orderReference").asText(null));
        if (paymentId == null) {
            log.warn("[WALLET] Callback ignored: unknown order reference");
            return;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("[WALLET] Callback ignored: payment #{} not found", paymentId);
            return;
        }

        String sessionId = event.path("sessionId").asText(null);
        JsonNode amountNode = event.path("amount");
        BigDecimal amount = amountNode.isNumber() ? amountNode.decimalValue() : null;
        if (amount == null || amount.compareTo(payment.getAmount()) != 0) {
            log.error("[WALLET] Callback amount mismatch for payment #{}, refunding session {}",
                    paymentId, sessionId);
            refundQuietly(sessionId);
            return;
        }

        completeGatewayPayment(paymentId, sessionId);
    }

    private void completeGatewayPayment(Long paymentId, String sessionId) {
        try {
            paymentWorker.completeGatewayPayment(paymentId);
            paymentWorker.onPaymentCompleted(paymentId);
            log.info("[WALLET] payment #{} completed", paymentId);
        } catch (PaymentWorker.PaymentFailedException failure) {
            String reason = failure.getReason();
            if ("ALREADY_HANDLED".equals(reason)) {
                return; // a concurrent callback/redirect completed it first
            }
            log.warn("[WALLET] payment #{} could not be completed: {}", paymentId, reason);
            if ("ALREADY_SOLD".equals(reason)) {
                paymentWorker.markFailed(paymentId, "ALREADY_SOLD");
                refundQuietly(sessionId);
            } else {
                paymentWorker.markFailed(paymentId, reason);
            }
        }
    }

    private CheckoutSession fetchSession(String sessionId) {
        try {
            return walletGateway.get(sessionId);
        } catch (WalletGatewayClient.GatewayException failure) {
            if ("CHECKOUT_NOT_FOUND".equals(failure.getCode())) {
                throw new InformationExistException(
                        "That wallet checkout could not be found. Please start the payment again.");
            }
            throw new InformationExistException(
                    "The wallet service is unavailable right now. Please try again shortly.");
        }
    }

    private void refundQuietly(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.error("[WALLET] No session id available for a refund; manual refund required");
            return;
        }
        try {
            walletGateway.refund(sessionId);
            log.info("[WALLET] Refunded session {} (item was already sold)", sessionId);
        } catch (WalletGatewayClient.GatewayException failure) {
            log.error("[WALLET] Refund failed for session {} ({}); manual refund required",
                    sessionId, failure.getCode());
        }
    }

    private String gatewayDescription(Listings listing) {
        String description = "Used Marketplace: " + listing.getTitle();
        return description.length() <= 140 ? description : description.substring(0, 140);
    }

    private String gatewayErrorMessage(String code) {
        return switch (code) {
            case "UNAUTHORIZED" -> "Wallet payments are not configured correctly. Please contact support.";
            case "VALIDATION_FAILED" -> "The wallet gateway rejected this order. Please try again.";
            case "ORDER_ALREADY_EXISTS" -> "A wallet payment already exists for this order. "
                    + "Refresh the page and try again.";
            case "TOO_MANY_REQUESTS" -> "Too many payment attempts. Please wait a moment and try again.";
            case "GATEWAY_UNAVAILABLE", "GATEWAY_INVALID_RESPONSE" ->
                    "The wallet service is unavailable right now. Please try again shortly.";
            default -> "The wallet payment could not be started. Please try again.";
        };
    }

    /** Local expiry of gateway sessions that were never paid (or never returned). */
    @Scheduled(fixedDelay = 60000)
    public void expireGatewayPayments() {
        expireStaleGatewayPayments();
    }

    private void expireStaleGatewayPayments() {
        List<Payment> stale = paymentRepository.findByMethodAndStatusAndExpiresAtBefore(
                Payment.Method.CARD, Payment.Status.PENDING, LocalDateTime.now());
        for (Payment payment : stale) {
            try {
                paymentWorker.markFailed(payment.getId(), "GATEWAY_EXPIRED");
                log.info("[WALLET] payment #{} marked expired", payment.getId());
            } catch (Exception failure) {
                log.error("[WALLET] could not expire payment #{}", payment.getId(), failure);
            }
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
