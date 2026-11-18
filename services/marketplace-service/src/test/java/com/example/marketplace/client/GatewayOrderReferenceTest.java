package com.example.marketplace.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayOrderReferenceTest {

    @Test
    void roundTripsThePaymentId() {
        String reference = GatewayOrderReference.create(42L);

        assertEquals(42L, GatewayOrderReference.paymentId(reference));
    }

    @Test
    void rejectsReferencesThatAreNotOurs() {
        assertNull(GatewayOrderReference.paymentId(null));
        assertNull(GatewayOrderReference.paymentId("ORDER-1042"));
        assertNull(GatewayOrderReference.paymentId("USED-abc-123"));
        assertNull(GatewayOrderReference.paymentId("USED--123"));
        assertNull(GatewayOrderReference.paymentId("USED-0-123"));
    }

    @Test
    void usesOnlyCharactersTheGatewayAccepts() {
        // letters, digits, dots, dashes and underscores only
        assertEquals(true, GatewayOrderReference.create(7L).matches("[A-Za-z0-9._-]{1,64}"));
    }
}
