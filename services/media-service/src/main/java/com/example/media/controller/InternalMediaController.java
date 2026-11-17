package com.example.media.controller;

import com.example.media.model.responses.MediaUploadResponse;
import com.example.media.service.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Internal upload API. Accounts and Marketplace check ownership before calling
 * this endpoint; browsers never upload here directly.
 */
@RestController
@RequestMapping("/api/internal/media")
public class InternalMediaController {

    private final MediaService mediaService;

    public InternalMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    public ResponseEntity<MediaUploadResponse> upload(
            @RequestParam("ownerId") Long ownerId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.store(ownerId, file));
    }
}
