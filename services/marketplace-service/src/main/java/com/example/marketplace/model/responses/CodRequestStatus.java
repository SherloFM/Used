package com.example.marketplace.model.responses;

import com.example.marketplace.model.Payment;
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

    public static CodRequestStatus from(Payment p, String buyerName) {
        return from(p, buyerName, 1);
    }

    public static CodRequestStatus from(Payment p, String buyerName, int queuePosition) {
        CodRequestStatus c = new CodRequestStatus();
        c.paymentId = p.getId();
        c.listingId = p.getListing() != null ? p.getListing().getId() : null;
        c.listingTitle = p.getListing() != null ? p.getListing().getTitle() : null;
        c.buyerName = buyerName;
        c.amount = p.getAmount() != null ? p.getAmount().toPlainString() : null;
        c.imgUrl = (p.getListing() != null) ? p.getListing().getImgUrl() : null;
        c.queuePosition = queuePosition;
        return c;
    }
}
