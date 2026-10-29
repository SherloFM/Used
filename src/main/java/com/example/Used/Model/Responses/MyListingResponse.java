package com.example.Used.Model.Responses;

import com.example.Used.Model.Listings;
import com.example.Used.Model.Transactions;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row of the profile page's "My Listings" tab: the listing with the
 * fields that explain its current state. SOLD rows carry the buyer,
 * DELISTED rows carry the reason. All other rows carry neither.
 */
@Getter
@Setter
public class MyListingResponse {
    private Long id;
    private String title;
    private double price;
    private Listings.Status status;
    private String imgUrl;
    private String statusReason;    // present when DELISTED
    private String buyerUsername;   // present when SOLD
    private LocalDateTime createdAt;

    public static MyListingResponse from(Listings listing, Transactions sale) {
        MyListingResponse response = new MyListingResponse();
        response.id = listing.getId();
        response.title = listing.getTitle();
        response.price = listing.getPrice();
        response.status = listing.getStatus();
        response.imgUrl = listing.getImgUrl();
        response.statusReason = listing.getStatusReason();
        response.createdAt = listing.getCreatedAt();
        if (sale != null) {
            response.buyerUsername = sale.getBuyer().getUsername() != null
                    ? sale.getBuyer().getUsername()
                    : sale.getBuyer().getEmail();
        }
        return response;
    }
}
