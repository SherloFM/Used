package com.example.marketplace.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListingsImageUrlTest {

    @Test
    void absoluteMediaUrlsBecomeRelativeSoAnyHostCanLoadThem() {
        Listings listing = new Listings();
        listing.setImgUrl("http://localhost:8088/api/media/4b6a");

        assertEquals("/api/media/4b6a", listing.getImgUrl());
    }

    @Test
    void httpsMediaUrlsFromTheOldPublicBaseAreHandledTheSameWay() {
        Listings listing = new Listings();
        listing.setImgUrl("https://used.thrifty.bid/api/media/4b6a");

        assertEquals("/api/media/4b6a", listing.getImgUrl());
    }

    @Test
    void unrelatedAndAlreadyRelativeUrlsAreUntouched() {
        Listings listing = new Listings();
        listing.setImgUrl("/images/laptop.jpg");
        assertEquals("/images/laptop.jpg", listing.getImgUrl());

        listing.setImgUrl("https://cdn.example.com/photo.jpg");
        assertEquals("https://cdn.example.com/photo.jpg", listing.getImgUrl());

        listing.setImgUrl(null);
        assertNull(listing.getImgUrl());
    }
}
