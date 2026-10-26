package com.example.Used.Service;

import com.example.Used.Model.Notification;
import com.example.Used.Repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> {
            List<SseEmitter> bucket = emittersByUser.get(userId);
            if (bucket != null) {
                bucket.remove(emitter);
                if (bucket.isEmpty()) emittersByUser.remove(userId, bucket);
            }
        };
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("Subscribed as user " + userId));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) emittersByUser.remove(userId);
        }
    }

    /** Persist + push. Returns saved notification. */
    public Notification sendToUser(Long userId, String eventName, Map<String, Object> data) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(eventName);
        n.setTitle(String.valueOf(data.getOrDefault("title", eventName)));
        n.setMessage(String.valueOf(data.getOrDefault("message", "")));
        Object pid = data.get("paymentId");
        if (pid instanceof Number) n.setPaymentId(((Number) pid).longValue());
        Object lid = data.get("listingId");
        if (lid instanceof Number) n.setListingId(((Number) lid).longValue());
        Object img = data.get("imgUrl");
        if (img != null) n.setImgUrl(String.valueOf(img));
        Notification saved = notificationRepository.save(n);

        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (IOException e) {
                    removeEmitter(userId, emitter);
                }
            }
        }
        return saved;
    }

    public List<Notification> history(Long userId, int page, int size) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .getContent();
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.markRead(notificationId, userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    public void broadcast(String eventName, Object data) {
        emittersByUser.values().forEach(bucket ->
                bucket.removeIf(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().name(eventName).data(data));
                        return false;
                    } catch (IOException e) {
                        return true;
                    }
                })
        );
    }
}
