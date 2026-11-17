package com.example.marketplace.client;

import java.util.UUID;

/** Response contract of Media's internal upload endpoint. */
public record MediaUploadResponse(
        UUID id,
        String path,
        String url,
        String contentType,
        long sizeBytes,
        String sha256) {
}
