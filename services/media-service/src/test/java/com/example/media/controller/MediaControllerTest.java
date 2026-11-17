package com.example.media.controller;

import com.example.media.model.MediaAsset;
import com.example.media.service.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaControllerTest {

    private final MediaService service = mock(MediaService.class);
    private final MediaController controller = new MediaController(service);

    @Test
    void returnsBytesWithLongLivedCacheAndEtag() {
        MediaAsset asset = asset();
        when(service.find(asset.getId())).thenReturn(asset);

        ResponseEntity<byte[]> response = controller.get(asset.getId(), null);

        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(asset.getContent(), response.getBody());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertEquals("\"" + asset.getSha256() + "\"", response.getHeaders().getETag());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.getHeaders().getCacheControl().contains("immutable"));
    }

    @Test
    void matchingEtagReturnsNotModifiedWithoutBody() {
        MediaAsset asset = asset();
        when(service.find(asset.getId())).thenReturn(asset);

        ResponseEntity<byte[]> response = controller.get(asset.getId(), "\"" + asset.getSha256() + "\"");

        assertEquals(304, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    private MediaAsset asset() {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setContentType("image/png");
        asset.setSizeBytes(4);
        asset.setSha256("a".repeat(64));
        asset.setContent(new byte[]{1, 2, 3, 4});
        return asset;
    }
}
