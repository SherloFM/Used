package com.example.Used.Model.Responses;

import com.example.Used.Model.Favorite;
import com.example.Used.Model.Listings;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class FavoriteResponse {

    private Long id;
    private Long listingId;
    private String listingTitle;
    private Double listingPrice;
    private String listingStatus;
    private String listingImgUrl;
    private LocalDateTime createdAt;

    public static FavoriteResponse from(Favorite favorite, Listings listing) {
        FavoriteResponse response = new FavoriteResponse();
        response.id = favorite.getId();
        response.listingId = favorite.getListingId();
        response.createdAt = favorite.getCreatedAt();
        if (listing != null) {
            response.listingTitle = listing.getTitle();
            response.listingPrice = listing.getPrice();
            response.listingStatus = listing.getStatus() != null ? listing.getStatus().name() : null;
            response.listingImgUrl = listing.getImgUrl();
        }
        return response;
    }
}
