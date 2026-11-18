package com.example.marketplace.model.responses;

import com.example.marketplace.client.CheckoutSession;
import lombok.Getter;
import lombok.Setter;

/**
 * What the checkout page needs to send the buyer to the gateway. It never
 * contains the API key or the signing secret.
 */
@Getter
@Setter
public class GatewayCheckoutResponse {
    private Long paymentId;
    private String sessionId;
    private String checkoutUrl;
    private String status;
    private String expiresAt;

    public static GatewayCheckoutResponse from(Long paymentId, CheckoutSession session) {
        GatewayCheckoutResponse response = new GatewayCheckoutResponse();
        response.paymentId = paymentId;
        response.sessionId = session.id();
        response.checkoutUrl = session.checkoutUrl();
        response.status = session.status();
        response.expiresAt = session.expiresAt();
        return response;
    }
}
