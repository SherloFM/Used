package com.example.notification.controller;

import com.example.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/notifications")
public class InternalNotificationController {
    private final NotificationService notificationService;

    public InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/push")
    public ResponseEntity<Void> push(@Valid @RequestBody PushRequest request) {
        notificationService.sendToUser(request.userId(), request.eventName(), request.data());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastRequest request) {
        notificationService.broadcast(request.eventName(), request.data());
        return ResponseEntity.noContent().build();
    }

    public record PushRequest(Long userId, String eventName, Map<String, Object> data) {}
    public record BroadcastRequest(String eventName, Object data) {}
}
