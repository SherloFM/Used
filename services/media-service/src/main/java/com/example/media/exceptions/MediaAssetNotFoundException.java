package com.example.media.exceptions;

import java.util.UUID;

public class MediaAssetNotFoundException extends RuntimeException {
    public MediaAssetNotFoundException(UUID id) {
        super("Image " + id + " was not found.");
    }
}
