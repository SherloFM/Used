package com.example.marketplace.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side client for the Almotawaj Wallet gateway.
 *
 * The API key never leaves the server: the browser only receives the
 * checkoutUrl. Callbacks are authenticated with the HMAC-SHA256 signature over
 * the exact request body, using the merchant signing secret.
 */
@Component
public class WalletGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(WalletGatewayClient.class);

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String signingSecret;

    public WalletGatewayClient(RestClient walletGatewayRestClient,
                               ObjectMapper json,
                               @Value("${wallet.gateway.api-key:}") String apiKey,
                               @Value("${wallet.gateway.signing-secret:}") String signingSecret) {
        this.http = walletGatewayRestClient;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.signingSecret = signingSecret == null ? "" : signingSecret.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public boolean canVerifyCallbacks() {
        return !signingSecret.isEmpty();
    }

    public CheckoutSession create(String orderReference,
                                  BigDecimal amount,
                                  String description,
                                  String returnUrl,
                                  int expiresInMinutes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderReference", orderReference);
        body.put("amount", amount);
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("expiresInMinutes", expiresInMinutes);
        try {
            CheckoutSession session = http.post()
                    .uri("/api/gateway/checkout-sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CheckoutSession.class);
            if (session == null || session.id() == null || session.checkoutUrl() == null) {
                throw new GatewayException("GATEWAY_INVALID_RESPONSE", 502, null);
            }
            return session;
        } catch (RestClientResponseException failure) {
            throw failed(failure);
        } catch (RestClientException failure) {
            throw new GatewayException("GATEWAY_UNAVAILABLE", 503, failure);
        }
    }

    public CheckoutSession get(String sessionId) {
        try {
            CheckoutSession session = http.get()
                    .uri("/api/gateway/checkout-sessions/{id}", sessionId)
                    .retrieve()
                    .body(CheckoutSession.class);
            if (session == null || session.status() == null) {
                throw new GatewayException("GATEWAY_INVALID_RESPONSE", 502, null);
            }
            return session;
        } catch (RestClientResponseException failure) {
            throw failed(failure);
        } catch (RestClientException failure) {
            throw new GatewayException("GATEWAY_UNAVAILABLE", 503, failure);
        }
    }

    /** Full refund of a paid session. Used when the item was sold twice. */
    public void refund(String sessionId) {
        try {
            http.post()
                    .uri("/api/gateway/checkout-sessions/{id}/refund", sessionId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException failure) {
            throw failed(failure);
        } catch (RestClientException failure) {
            throw new GatewayException("GATEWAY_UNAVAILABLE", 503, failure);
        }
    }

    /**
     * Constant-time check of {@code X-Wallet-Signature}: the hex HMAC-SHA256 of
     * the exact raw body. A missing secret or a malformed header never verifies.
     */
    public boolean validSignature(String rawBody, String signature) {
        if (signingSecret.isEmpty() || rawBody == null || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(signature.trim());
            return MessageDigest.isEqual(expected, received);
        } catch (Exception failure) {
            log.warn("[WALLET] Rejected callback with an unreadable signature");
            return false;
        }
    }

    public ObjectMapper json() {
        return json;
    }

    private GatewayException failed(RestClientResponseException failure) {
        String code = "GATEWAY_ERROR";
        try {
            JsonNode problem = json.readTree(failure.getResponseBodyAsString());
            if (problem != null && problem.hasNonNull("code")) {
                code = problem.get("code").asText();
            }
        } catch (Exception ignored) {
            // A non-JSON error body keeps the generic code.
        }
        return new GatewayException(code, failure.getStatusCode().value(), failure);
    }

    /** Carries the gateway's stable error code, so callers never branch on text. */
    public static class GatewayException extends RuntimeException {
        private final String code;
        private final int status;

        public GatewayException(String code, int status, Throwable cause) {
            super(code, cause);
            this.code = code;
            this.status = status;
        }

        public String getCode() {
            return code;
        }

        public int getStatus() {
            return status;
        }
    }
}
