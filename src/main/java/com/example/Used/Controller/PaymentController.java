package com.example.Used.Controller;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.Payment;
import com.example.Used.Model.Responses.CodRequestStatus;
import com.example.Used.Model.Responses.PaymentResult;
import com.example.Used.Model.Responses.PaymentStatus;
import com.example.Used.Service.PaymentService;
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
