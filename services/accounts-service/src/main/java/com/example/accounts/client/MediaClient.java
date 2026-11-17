package com.example.accounts.client;

import com.example.accounts.exceptions.InformationExistException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Accounts checks the current user and calls Media before changing the profile.
 * Media only receives bytes; it trusts the caller's ownerId as audit metadata.
 */
@Component
public class MediaClient {

    private final RestClient http;

    public MediaClient(RestClient mediaRestClient) {
        this.http = mediaRestClient;
    }

    public MediaUploadResponse upload(Long ownerId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException failure) {
            throw new InformationExistException("That image could not be read. Please choose another file.");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("ownerId", ownerId.toString());
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "image";
            }
        });

        MediaUploadResponse response;
        try {
            response = http.post()
                    .uri("/api/internal/media")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(MediaUploadResponse.class);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status == 400 || status == 413) {
                throw new InformationExistException(
                        "Please choose a valid JPEG or PNG image no larger than 5 MB.");
            }
            throw new IllegalStateException("Media upload failed with status " + status, failure);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Media service is unavailable", failure);
        }

        if (response == null || response.url() == null || response.url().isBlank()) {
            throw new IllegalStateException("Media upload returned no image URL");
        }
        return response;
    }
}
