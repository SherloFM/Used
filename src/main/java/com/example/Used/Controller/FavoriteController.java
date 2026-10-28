package com.example.Used.Controller;

import com.example.Used.Model.Responses.FavoriteResponse;
import com.example.Used.Service.FavoriteService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{listingId}")
    public ResponseEntity<FavoriteResponse> add(@PathVariable Long listingId) {
        return ResponseEntity.ok(favoriteService.addFavorite(listingId));
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> remove(@PathVariable Long listingId) {
        favoriteService.removeFavorite(listingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Page<FavoriteResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return favoriteService.getMyFavorites(page, size);
    }

    @GetMapping("/{listingId}/check")
    public ResponseEntity<Map<String, Boolean>> check(@PathVariable Long listingId) {
        return ResponseEntity.ok(Map.of("favorited", favoriteService.isFavorite(listingId)));
    }

    @GetMapping("/{listingId}/count")
    public ResponseEntity<Map<String, Long>> count(@PathVariable Long listingId) {
        return ResponseEntity.ok(Map.of("count", favoriteService.countFavorites(listingId)));
    }
}
