package com.example.Used.Model.Responses;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentResult {
    private Long paymentId;
    private boolean duplicate;
    private int queuePosition;

    public static PaymentResult duplicate(Long paymentId) {
        return duplicate(paymentId, 1);
    }

    public static PaymentResult duplicate(Long paymentId, int position) {
        PaymentResult r = new PaymentResult();
        r.paymentId = paymentId;
        r.duplicate = true;
        r.queuePosition = position;
        return r;
    }

    public static PaymentResult accepted(Long paymentId) {
        return accepted(paymentId, 1);
    }

    public static PaymentResult accepted(Long paymentId, int position) {
        PaymentResult r = new PaymentResult();
        r.paymentId = paymentId;
        r.duplicate = false;
        r.queuePosition = position;
        return r;
    }
}
