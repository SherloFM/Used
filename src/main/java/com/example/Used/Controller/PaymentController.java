package com.example.Used.Controller;

import com.example.Used.Model.Responses.PaymentResult;
import com.example.Used.Model.Responses.PaymentStatus;
import com.example.Used.Service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/payments/{listingId}  -> 202 Accepted, hands off to the pool
 * GET  /api/payments/{id}         -> poll the outcome
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{listingId}")
    public ResponseEntity<PaymentResult> buy(@PathVariable Long listingId) {
        PaymentResult result = paymentService.submitPayment(listingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/{id}")
    public PaymentStatus status(@PathVariable Long id) {
        return PaymentStatus.from(paymentService.getPayment(id));
    }
}
