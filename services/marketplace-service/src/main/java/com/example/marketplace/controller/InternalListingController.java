package com.example.marketplace.controller;

import com.example.marketplace.messaging.DelistOutcome;
import com.example.marketplace.model.requests.InternalDelistRequest;
import com.example.marketplace.model.responses.ListingSummaryResponse;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.service.ListingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/listings")
public class InternalListingController {
    private final ListingRepository listingRepository;
    private final ListingService listingService;

    public InternalListingController(ListingRepository listingRepository, ListingService listingService) {
        this.listingRepository = listingRepository;
        this.listingService = listingService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingSummaryResponse> getListing(@PathVariable Long id) {
        return listingRepository.findById(id)
                .map(listing -> new ListingSummaryResponse(
                        listing.getId(), listing.getUserId(), listing.getTitle(), listing.getPrice(),
                        listing.getStatus() == null ? null : listing.getStatus().name(), listing.getImgUrl()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Idempotent delist. Delisting an already delisted listing is success, not a
     * conflict, because the caller may be retrying a command whose earlier
     * attempt it never saw the result of.
     */
    @PostMapping("/{id}/delist")
    public ResponseEntity<Map<String, String>> delist(@PathVariable Long id,
                                                      @Valid @RequestBody InternalDelistRequest request) {
        DelistOutcome outcome = listingService.adminDeleteListingAs(
                id, request.reason(), request.adminId(), request.adminUsername());

        return switch (outcome) {
            case DELISTED, ALREADY_DELISTED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Listing not found"));
            case SOLD -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A sold listing cannot be removed"));
            case FAILED -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Listing could not be delisted"));
        };
    }
}
