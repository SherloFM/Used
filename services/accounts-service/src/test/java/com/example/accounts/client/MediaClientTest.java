package com.example.accounts.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MediaClientTest {

    @Test
    void uploadsOwnerAndFileAsMultipartWithInternalToken() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {"id":"%s","path":"/api/media/%s","url":"http://localhost:8088/api/media/%s",
                 "contentType":"image/png","sizeBytes":3,"sha256":"abc"}
                """.formatted(id, id, id);
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/media", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RestClient http = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .defaultHeader("X-Internal-Token", "token")
                    .build();

            MediaUploadResponse response = new MediaClient(http).upload(7L,
                    new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3}));

            assertEquals(id, response.id());
            assertEquals("http://localhost:8088/api/media/" + id, response.url());
            assertEquals("token", token.get());
            assertTrue(body.get().contains("name=\"ownerId\""), body.get());
            assertTrue(body.get().contains("name=\"file\""), body.get());
            assertTrue(body.get().contains("filename=\"image\""), body.get());
        } finally {
            server.stop(0);
        }
    }
}
