package com.example.marketplace.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletGatewayClientTest {

    private static final String SECRET = "test-signing-secret";
    private static final String BODY =
            "{\"event\":\"checkout.paid\",\"sessionId\":\"s1\",\"orderReference\":\"USED-9-1\","
                    + "\"amount\":12.5,\"status\":\"PAID\"}";

    private WalletGatewayClient client(String secret) {
        return client("test-key", secret);
    }

    private WalletGatewayClient client(String apiKey, String secret) {
        return new WalletGatewayClient(null, JsonMapper.builder().build(), apiKey, secret);
    }

    @Test
    void acceptsTheHexHmacOfTheExactBody() throws Exception {
        assertTrue(client(SECRET).validSignature(BODY, sign(BODY, SECRET)));
    }

    @Test
    void rejectsATamperedBodyOrASignatureFromAnotherSecret() throws Exception {
        assertFalse(client(SECRET).validSignature(BODY + " ", sign(BODY, SECRET)));
        assertFalse(client(SECRET).validSignature(BODY, sign(BODY, "other-secret")));
    }

    @Test
    void rejectsMissingOrMalformedSignatures() {
        assertFalse(client(SECRET).validSignature(BODY, null));
        assertFalse(client(SECRET).validSignature(BODY, "not-hex"));
        assertFalse(client(SECRET).validSignature(BODY, ""));
    }

    @Test
    void callbacksCannotBeVerifiedWithoutAConfiguredSecret() throws Exception {
        assertFalse(client("").validSignature(BODY, sign(BODY, SECRET)));
        assertFalse(client("").canVerifyCallbacks());
    }

    @Test
    void configuredOnlyWhenAnApiKeyExists() {
        assertTrue(client(SECRET).isConfigured());
        assertFalse(client("", SECRET).isConfigured());
    }

    private String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
