package com.example.Used.Service;

import com.example.Used.Model.Notification;
import com.example.Used.Repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
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

    public SseEmitter subscribe(Long userId, Long lastEventId) {
        SseEmitter emitter = createEmitter();
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("Subscribed as user " + userId));
            if (lastEventId != null && lastEventId >= 0) {
                notificationRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(userId, lastEventId)
                        .forEach(notification -> sendNotificationEvent(userId, emitter, notification));
            }
        } catch (IOException | IllegalStateException e) {
            removeEmitter(userId, emitter);
        } catch (RuntimeException e) {
            removeEmitter(userId, emitter);
            throw e;
        }
        return emitter;
    }

    SseEmitter createEmitter() {
        return new SseEmitter(Long.MAX_VALUE);
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) emittersByUser.remove(userId);
        }
    }

    /** Persist + push. Returns saved notification. */
    @Transactional
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

        Map<String, Object> eventData = new HashMap<>(data);
        eventData.put("notificationId", saved.getId());
        eventData.put("eventType", eventName);
        eventData.put("createdAt", saved.getCreatedAt());
        Runnable publish = () -> publishToUser(userId, eventName, eventData);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
        return saved;
    }

    void publishToUser(Long userId, String eventName, Map<String, Object> data) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    sendEvent(emitter, eventName, data);
                    if (!"NOTIFICATION".equals(eventName)) {
                        sendEvent(emitter, "NOTIFICATION", data);
                    }
                } catch (IOException | IllegalStateException e) {
                    removeEmitter(userId, emitter);
                }
            }
        }
    }

    private void sendNotificationEvent(Long userId, SseEmitter emitter, Notification notification) {
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", notification.getId());
        data.put("eventType", notification.getType());
        data.put("title", notification.getTitle());
        data.put("message", notification.getMessage());
        data.put("type", notification.getType());
        data.put("createdAt", notification.getCreatedAt());
        if (notification.getPaymentId() != null) data.put("paymentId", notification.getPaymentId());
        if (notification.getListingId() != null) data.put("listingId", notification.getListingId());
        if (notification.getImgUrl() != null) data.put("imgUrl", notification.getImgUrl());

        try {
            sendEvent(emitter, notification.getType(), data);
            if (!"NOTIFICATION".equals(notification.getType())) {
                sendEvent(emitter, "NOTIFICATION", data);
            }
        } catch (IOException | IllegalStateException e) {
            removeEmitter(userId, emitter);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> data)
            throws IOException {
        Object notificationId = data.get("notificationId");
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventName).data(data);
        if (notificationId != null) {
            event.id(String.valueOf(notificationId));
        }
        emitter.send(event);
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
        Runnable publish = () -> emittersByUser.values().forEach(bucket ->
                bucket.removeIf(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().name(eventName).data(data));
                        return false;
                    } catch (IOException | IllegalStateException e) {
                        return true;
                    }
                }));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
