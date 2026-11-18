package com.example.marketplace.exceptions;

/** Raised when a gateway callback cannot be authenticated with the signing secret. */
public class GatewaySignatureException extends RuntimeException {

    public GatewaySignatureException() {
        super("Invalid wallet callback signature");
    }
}
