package com.example.marketplace.controller;

import com.example.marketplace.exceptions.GatewaySignatureException;
import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.model.Payment;
import com.example.marketplace.model.responses.CodRequestStatus;
import com.example.marketplace.model.responses.GatewayCheckoutResponse;
import com.example.marketplace.model.responses.PaymentResult;
import com.example.marketplace.model.responses.PaymentStatus;
import com.example.marketplace.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * POST /api/payments/{listingId}?method=WALLET|COD -> 202 Accepted
 * GET  /api/payments/{id}                        -> poll the outcome
 * GET  /api/payments/cod/pending                 -> seller's COD requests
 * POST /api/payments/{id}/cod/accept             -> seller accepts
 * POST /api/payments/{id}/cod/decline            -> seller declines
 * POST /api/payments/{listingId}/gateway         -> Almotawaj Wallet checkout session
 * GET  /api/payments/{id}/gateway?sessionId=...  -> confirm the returned checkout
 * POST /api/payments/gateway/callback            -> signed gateway callback
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{listingId}")
    public ResponseEntity<PaymentResult> buy(
            @PathVariable Long listingId,
            @RequestParam(value = "method", required = false) String method
    ) {
        PaymentResult result = paymentService.submitPayment(listingId, parseMethod(method));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/{id}")
    public PaymentStatus status(@PathVariable Long id) {
        return paymentService.getPaymentStatus(id);
    }

    @GetMapping("/cod/pending")
    public List<CodRequestStatus> pendingCodRequests() {
        return paymentService.getPendingCodRequests();
    }

    @PostMapping("/{id}/cod/accept")
    public PaymentStatus acceptCod(@PathVariable Long id) {
        return paymentService.acceptCod(id);
    }

    @PostMapping("/{id}/cod/decline")
    public PaymentStatus declineCod(@PathVariable Long id) {
        return paymentService.declineCod(id);
    }

    // ------------------------------------------------------------------
    // Almotawaj Wallet gateway
    // ------------------------------------------------------------------

    @PostMapping("/{listingId}/gateway")
    public ResponseEntity<GatewayCheckoutResponse> startGateway(@PathVariable Long listingId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.startGatewayPayment(listingId));
    }

    @GetMapping("/{id}/gateway")
    public PaymentStatus confirmGateway(@PathVariable Long id,
                                        @RequestParam("sessionId") String sessionId) {
        return paymentService.confirmGatewayPayment(id, sessionId);
    }

    /**
     * Public on purpose: the gateway authenticates itself with the
     * X-Wallet-Signature HMAC over the raw body, not with a browser session.
     */
    @PostMapping("/gateway/callback")
    public ResponseEntity<Void> gatewayCallback(
            @RequestHeader(value = "X-Wallet-Signature", required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        try {
            paymentService.handleGatewayCallback(rawBody == null ? "" : rawBody, signature);
            return ResponseEntity.ok().build();
        } catch (GatewaySignatureException failure) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private Payment.Method parseMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return Payment.Method.WALLET;
        }
        try {
            return Payment.Method.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InformationExistException("Unknown payment method");
        }
    }
}
