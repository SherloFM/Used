package com.example.Used.Model.Responses;

import com.example.Used.Model.Payment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodRequestStatus {
    private Long paymentId;
    private Long listingId;
    private String listingTitle;
    private String buyerName;
    private String amount;
    private String imgUrl;
    private int queuePosition;

    public static CodRequestStatus from(Payment p) {
        return from(p, 1);
    }

    public static CodRequestStatus from(Payment p, int queuePosition) {
        CodRequestStatus c = new CodRequestStatus();
        c.paymentId = p.getId();
        c.listingId = p.getListing() != null ? p.getListing().getId() : null;
        c.listingTitle = p.getListing() != null ? p.getListing().getTitle() : null;
        c.buyerName = p.getBuyer() != null ? p.getBuyer().getUsername() : null;
        c.amount = p.getAmount() != null ? p.getAmount().toPlainString() : null;
        c.imgUrl = (p.getListing() != null) ? p.getListing().getImgUrl() : null;
        c.queuePosition = queuePosition;
        return c;
    }
}
