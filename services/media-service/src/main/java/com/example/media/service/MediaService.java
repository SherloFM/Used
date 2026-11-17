package com.example.media.service;

import com.example.media.exceptions.InvalidMediaException;
import com.example.media.exceptions.MediaAssetNotFoundException;
import com.example.media.model.MediaAsset;
import com.example.media.model.responses.MediaUploadResponse;
import com.example.media.repository.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores immutable images after verifying the actual file contents.
 *
 * The caller's MIME type and file extension are advisory only. The format is
 * derived from the bytes, the image must decode, and size/dimension limits are
 * enforced before anything is persisted.
 */
@Service
public class MediaService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8_000;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final String UNREADABLE = "That image could not be read. Please choose a JPEG or PNG file.";

    private final MediaAssetRepository repository;
    private final String publicBaseUrl;

    public MediaService(MediaAssetRepository repository,
            @Value("${media.public-base-url:}") String publicBaseUrl) {
        this.repository = repository;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public MediaUploadResponse store(Long ownerId, MultipartFile file) throws IOException {
        if (ownerId == null || ownerId <= 0) {
            throw new InvalidMediaException("A valid image owner is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("Please choose an image file.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidMediaException("Image is too large. Maximum size is 5 MB.");
        }

        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new InvalidMediaException("Please choose an image file.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidMediaException("Image is too large. Maximum size is 5 MB.");
        }

        ImageInfo image = inspect(bytes);
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setOwnerId(ownerId);
        asset.setContentType(image.contentType());
        asset.setSizeBytes(bytes.length);
        asset.setSha256(sha256(bytes));
        asset.setContent(bytes);
        asset.setCreatedAt(Instant.now());
        repository.save(asset);

        String path = "/api/media/" + asset.getId();
        return new MediaUploadResponse(asset.getId(), path, publicUrl(path),
                asset.getContentType(), asset.getSizeBytes(), asset.getSha256());
    }

    public MediaAsset find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new MediaAssetNotFoundException(id));
    }

    private String publicUrl(String path) {
        return publicBaseUrl.isEmpty() ? path : publicBaseUrl + path;
    }

    private ImageInfo inspect(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new InvalidMediaException(UNREADABLE);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidMediaException(UNREADABLE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String contentType = contentType(reader.getFormatName());
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new InvalidMediaException(UNREADABLE);
                }
                if (width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new InvalidMediaException(
                            "Image dimensions are too large. Maximum is 8000 x 8000 pixels.");
                }
                BufferedImage decoded;
                try {
                    decoded = reader.read(0);
                } catch (IOException | RuntimeException failure) {
                    throw new InvalidMediaException(UNREADABLE);
                }
                if (decoded == null) {
                    throw new InvalidMediaException(UNREADABLE);
                }
                return new ImageInfo(contentType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException failure) {
            throw new InvalidMediaException(UNREADABLE);
        }
    }

    private String contentType(String formatName) {
        String format = formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
        return switch (format) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> throw new InvalidMediaException("Only JPEG and PNG images are supported.");
        };
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", failure);
        }
    }

    private record ImageInfo(String contentType, int width, int height) {
    }
}
