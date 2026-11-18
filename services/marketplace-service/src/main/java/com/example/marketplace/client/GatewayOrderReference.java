package com.example.marketplace.client;

/**
 * The gateway allows one session per order reference. Marketplace does not have
 * a column for the gateway session id (no schema changes), so the payment id is
 * encoded in the reference instead: the return redirect and the signed callback
 * both carry it back, and {@link #paymentId(String)} recovers the row.
 *
 * Format: USED-{paymentId}-{epochMillis} (letters, digits and dashes only).
 */
public final class GatewayOrderReference {

    private static final String PREFIX = "USED-";

    private GatewayOrderReference() {
    }

    public static String create(Long paymentId) {
        return PREFIX + paymentId + "-" + System.currentTimeMillis();
    }

    /** Null when the reference did not come from this marketplace. */
    public static Long paymentId(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            return null;
        }
        int end = reference.indexOf('-', PREFIX.length());
        String raw = end < 0 ? reference.substring(PREFIX.length())
                : reference.substring(PREFIX.length(), end);
        try {
            long id = Long.parseLong(raw);
            return id > 0 ? id : null;
        } catch (NumberFormatException failure) {
            return null;
        }
    }
}
