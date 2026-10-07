package com.example.Used.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
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
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Subscribed as user " + userId));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByUser.remove(userId);
            }
        }
    }


    public void sendToUser(Long userId, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null && !emitters.isEmpty()) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(eventName)
                            .data(data));
                } catch (IOException e) {
                    // Remove broken connections
                    removeEmitter(userId, emitter);
                }
            }
        }
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
