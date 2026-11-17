package com.example.media.controller;

import com.example.media.model.MediaAsset;
import com.example.media.service.MediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Public image retrieval. Assets are immutable, so browsers may cache them for
 * a year; a replacement upload gets a new id and URL.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365))
            .cachePublic().immutable();

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(
            @PathVariable UUID id,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        MediaAsset asset = mediaService.find(id);
        String etag = "\"" + asset.getSha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(CACHE).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .contentLength(asset.getSizeBytes())
                .eTag(etag)
                .cacheControl(CACHE)
                .header("X-Content-Type-Options", "nosniff")
                .body(asset.getContent());
    }
}
