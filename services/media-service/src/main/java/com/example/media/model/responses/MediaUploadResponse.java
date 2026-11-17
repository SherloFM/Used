package com.example.media.model.responses;

import java.util.UUID;

/** Upload result consumed by Accounts and Marketplace. */
public record MediaUploadResponse(
        UUID id,
        String path,
        String url,
        String contentType,
        long sizeBytes,
        String sha256) {
}
