package com.example.media.service;

import com.example.media.exceptions.InvalidMediaException;
import com.example.media.exceptions.MediaAssetNotFoundException;
import com.example.media.model.MediaAsset;
import com.example.media.model.responses.MediaUploadResponse;
import com.example.media.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaServiceTest {

    private final MediaAssetRepository repository = mock(MediaAssetRepository.class);

    @Test
    void storesVerifiedPngAndBuildsImmutableUrl() throws IOException {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MediaService service = new MediaService(repository, "http://localhost:8088/");

        MediaUploadResponse response = service.store(7L, png(2, 2));

        assertEquals("image/png", response.contentType());
        assertEquals(64, response.sha256().length());
        assertTrue(response.sizeBytes() > 0);
        assertEquals("http://localhost:8088/api/media/" + response.id(), response.url());
        assertEquals("/api/media/" + response.id(), response.path());
        verify(repository).save(any(MediaAsset.class));
    }

    @Test
    void derivesJpegTypeFromBytesNotFromDeclaredName() throws IOException {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MediaService service = new MediaService(repository, "");
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        var file = new MockMultipartFile("file", "photo.txt", "text/plain", output.toByteArray());

        MediaUploadResponse response = service.store(7L, file);

        assertEquals("image/jpeg", response.contentType());
        assertTrue(response.url().startsWith("/api/media/"));
    }

    @Test
    void rejectsTextThatClaimsToBeAnImage() {
        MediaService service = new MediaService(repository, "");
        var file = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
        assertThrows(InvalidMediaException.class, () -> service.store(7L, file));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnsupportedFormatEvenWhenDecodable() throws IOException {
        MediaService service = new MediaService(repository, "");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", output);
        var file = new MockMultipartFile("file", "animation.gif", "image/gif", output.toByteArray());

        assertThrows(InvalidMediaException.class, () -> service.store(7L, file));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsOversizedFilesBeforeReadingThem() {
        MediaService service = new MediaService(repository, "");
        var file = new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        assertThrows(InvalidMediaException.class, () -> service.store(7L, file));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsEmptyFileAndMissingOwner() {
        MediaService service = new MediaService(repository, "");
        assertThrows(InvalidMediaException.class,
                () -> service.store(7L, new MockMultipartFile("file", "empty.png", "image/png", new byte[0])));
        assertThrows(InvalidMediaException.class, () -> service.store(0L, png(1, 1)));
        verifyNoInteractions(repository);
    }

    @Test
    void missingAssetBecomesNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(MediaAssetNotFoundException.class,
                () -> new MediaService(repository, "").find(id));
    }

    private MockMultipartFile png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());
    }
}
