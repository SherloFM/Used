package com.example.favorites.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class NotificationClient {
    private final RestClient http;

    public NotificationClient(@Qualifier("notificationRestClient") RestClient http) {
        this.http = http;
    }

    public void sendToUser(Long userId, String eventName, Map<String, Object> data) {
        http.post().uri("/api/internal/notifications/push")
                .body(new PushRequest(userId, eventName, data)).retrieve().toBodilessEntity();
    }

    public void broadcast(String eventName, Object data) {
        http.post().uri("/api/internal/notifications/broadcast")
                .body(new BroadcastRequest(eventName, data)).retrieve().toBodilessEntity();
    }

    public record PushRequest(Long userId, String eventName, Map<String, Object> data) {}
    public record BroadcastRequest(String eventName, Object data) {}
}
