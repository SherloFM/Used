package com.example.Used.Controller;

import com.example.Used.Model.Listings;
import com.example.Used.Service.ListingService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/listings")
public class ListingsController {

    private ListingService listingsService;

    // CREATE
    @PostMapping
    public Listings createListing(
            @RequestBody Listings listing
    ) {
        return listingsService.createListing(listing);
    }

    // GET ALL
    @GetMapping
    public List<Listings> getListings() {
        return listingsService.getListings();
    }

    // GET ONE
    @GetMapping("/{id}")
    public Listings getListing(
            @PathVariable Long id
    ) {
        return listingsService.getListing(id);
    }

    // UPDATE
    @PutMapping("/{id}")
    public Listings updateListing(
            @PathVariable Long id,
            @RequestBody Listings listing
    ) {
        return listingsService.updateListing(id, listing);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteListing(
            @PathVariable Long id
    ) {
        listingsService.deleteListing(id);

        return ResponseEntity.ok(
                "Listing deleted successfully"
        );
    }

    // UPLOAD IMAGE
    @PutMapping("/{id}/image")
    public Listings uploadImage(
            @PathVariable Long id,
            @RequestParam("img") MultipartFile img
    ) throws IOException {

        return listingsService.uploadImage(id, img);
    }

    // BUY
    @PostMapping("/{id}/buy")
    public Listings buyListing(
            @PathVariable Long id
    ) {
        return listingsService.buyListing(id);
    }
}