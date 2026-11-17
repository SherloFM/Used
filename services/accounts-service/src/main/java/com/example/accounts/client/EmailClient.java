package com.example.accounts.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EmailClient {

    private final RestClient http;

    public EmailClient(RestClient emailRestClient) {
        this.http = emailRestClient;
    }

    public void sendVerificationEmail(String to, String code) {
        http.post().uri("/api/internal/email/verification")
                .body(new EmailRequests.VerificationRequest(to, code))
                .retrieve().toBodilessEntity();
    }

    public void sendPasswordResetEmail(String to, String code) {
        http.post().uri("/api/internal/email/password-reset")
                .body(new EmailRequests.PasswordResetRequest(to, code))
                .retrieve().toBodilessEntity();
    }

    public void sendAccountBannedEmail(String to) {
        http.post().uri("/api/internal/email/account-banned")
                .body(new EmailRequests.ToRequest(to))
                .retrieve().toBodilessEntity();
    }

    public void sendAccountUnbannedEmail(String to) {
        http.post().uri("/api/internal/email/account-unbanned")
                .body(new EmailRequests.ToRequest(to))
                .retrieve().toBodilessEntity();
    }

    public void sendAccountDeletedEmail(String to) {
        http.post().uri("/api/internal/email/account-deleted")
                .body(new EmailRequests.ToRequest(to))
                .retrieve().toBodilessEntity();
    }
}
