package com.example.Used.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationService {

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // add to this user's bucket (create bucket if first connection)
        emittersByUser
                .computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        // on disconnect, remove THIS emitter from THIS user's bucket
        Runnable cleanup = () -> {
            List<SseEmitter> bucket = emittersByUser.get(userId);
            if (bucket != null) {
                bucket.remove(emitter);
                if (bucket.isEmpty()) {
                    emittersByUser.remove(userId, bucket); // drop empty bucket
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Subscribed as user " + userId));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }


    public void sendToUser(Long userId, String eventName, Object data) {
        List<SseEmitter> bucket = emittersByUser.get(userId);
        if (bucket == null || bucket.isEmpty()) {
            return; // they have no open stream; the audit row is still saved
        }
        bucket.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                return false;
            } catch (IOException e) {
                return true; // dead -> drop
            }
        });
    }

}
