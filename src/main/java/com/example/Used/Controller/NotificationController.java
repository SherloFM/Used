package com.example.Used.Controller;

import com.example.Used.Model.Notification;
import com.example.Used.Model.User;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                 CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream() {
        User me = currentUserService.getCurrentUser();
        return notificationService.subscribe(me.getId());
    }

    @GetMapping
    public List<Notification> history(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        User me = currentUserService.getCurrentUser();
        return notificationService.history(me.getId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        User me = currentUserService.getCurrentUser();
        return Map.of("count", notificationService.unreadCount(me.getId()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        User me = currentUserService.getCurrentUser();
        notificationService.markRead(me.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        User me = currentUserService.getCurrentUser();
        notificationService.markAllRead(me.getId());
        return ResponseEntity.noContent().build();
    }
}
