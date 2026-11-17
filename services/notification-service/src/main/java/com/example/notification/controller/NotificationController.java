package com.example.notification.controller;

import com.example.notification.model.Notification;
import com.example.notification.security.CurrentActorProvider;
import com.example.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentActorProvider currentActorProvider;

    public NotificationController(NotificationService notificationService,
                                  CurrentActorProvider currentActorProvider) {
        this.notificationService = notificationService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        Long me = currentActorProvider.get().id();
        return notificationService.subscribe(me, lastEventId);
    }

    @GetMapping
    public List<Notification> history(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Long me = currentActorProvider.get().id();
        return notificationService.history(me, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        Long me = currentActorProvider.get().id();
        return Map.of("count", notificationService.unreadCount(me));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        Long me = currentActorProvider.get().id();
        notificationService.markRead(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        Long me = currentActorProvider.get().id();
        notificationService.markAllRead(me);
        return ResponseEntity.noContent().build();
    }
}
