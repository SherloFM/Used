package com.example.favorites.model.responses;

import com.example.favorites.model.Favorite;
import com.example.favorites.client.ListingSummary;
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

    public static FavoriteResponse from(Favorite favorite, ListingSummary listing) {
        FavoriteResponse response = new FavoriteResponse();
        response.id = favorite.getId();
        response.listingId = favorite.getListingId();
        response.createdAt = favorite.getCreatedAt();
        if (listing != null) {
            response.listingTitle = listing.title();
            response.listingPrice = listing.price();
            response.listingStatus = listing.status();
            response.listingImgUrl = listing.imgUrl();
        }
        return response;
    }
}
