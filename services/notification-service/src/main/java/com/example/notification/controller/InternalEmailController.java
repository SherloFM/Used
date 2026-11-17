package com.example.notification.controller;

import com.example.notification.service.EmailServices;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("api/internal/email")
public class InternalEmailController {

    private final EmailServices emailServices;

    public InternalEmailController(EmailServices emailServices) {
        this.emailServices = emailServices;
    }

    @PostMapping("/verification")
    public ResponseEntity<Void> verification(@Valid @RequestBody VerificationRequest r) {
        emailServices.sendVerificationEmail(r.to(), r.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> passwordReset(@Valid @RequestBody PasswordResetRequest r) {
        emailServices.sendPasswordResetEmail(r.to(), r.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-banned")
    public ResponseEntity<Void> accountBanned(@Valid @RequestBody ToRequest r) {
        emailServices.sendAccountBannedEmail(r.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-unbanned")
    public ResponseEntity<Void> accountUnbanned(@Valid @RequestBody ToRequest r) {
        emailServices.sendAccountUnbannedEmail(r.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-deleted")
    public ResponseEntity<Void> accountDeleted(@Valid @RequestBody ToRequest r) {
        emailServices.sendAccountDeletedEmail(r.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listing-deleted")
    public ResponseEntity<Void> listingDeleted(@Valid @RequestBody ListingEmailRequest r) {
        emailServices.sendListingDeletedEmail(r.to(), r.listingTitle());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listing-sold")
    public ResponseEntity<Void> listingSold(@Valid @RequestBody ListingEmailRequest r) {
        emailServices.sendListingSoldEmail(r.to(), r.listingTitle());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cod-request")
    public ResponseEntity<Void> codRequest(@Valid @RequestBody CodRequestEmail r) {
        emailServices.sendCodRequestEmail(r.sellerEmail(), r.listingTitle(), r.buyerName(), r.amount());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cod-accepted")
    public ResponseEntity<Void> codAccepted(@Valid @RequestBody ListingEmailRequest r) {
        emailServices.sendCodAcceptedEmail(r.to(), r.listingTitle());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cod-declined")
    public ResponseEntity<Void> codDeclined(@Valid @RequestBody ListingEmailRequest r) {
        emailServices.sendCodDeclinedEmail(r.to(), r.listingTitle());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listing-unavailable")
    public ResponseEntity<Void> listingUnavailable(@Valid @RequestBody ListingUnavailableEmail r) {
        emailServices.sendListingUnavailableEmail(r.to(), r.listingTitle(), r.reason());
        return ResponseEntity.noContent().build();
    }

    public record ToRequest(@Email @NotBlank String to) {}
    public record VerificationRequest(String to, String code) {}
    public record PasswordResetRequest(String to, String code) {}
    public record ListingEmailRequest(String to, String listingTitle) {}
    public record CodRequestEmail(String sellerEmail, String listingTitle, String buyerName, BigDecimal amount) {}
    public record ListingUnavailableEmail(String to, String listingTitle, String reason) {}
}
