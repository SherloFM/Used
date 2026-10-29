package com.example.Used.Service;

import com.example.Used.Model.Notification;
import com.example.Used.Repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceReplayTest {

    @Test
    void reconnectReplaysOnlyThatUsersNotificationsAfterLastEventId() throws Exception {
        NotificationRepository repository = mock(NotificationRepository.class);
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationService service = new NotificationService(repository) {
            @Override
            SseEmitter createEmitter() {
                return emitter;
            }
        };
        Notification notification = new Notification();
        notification.setId(42L);
        notification.setUserId(7L);
        notification.setType("LISTING_UPDATED");
        notification.setTitle("Saved listing updated");
        notification.setMessage("The price changed.");
        when(repository.findByUserIdAndIdGreaterThanOrderByIdAsc(7L, 40L))
                .thenReturn(List.of(notification));

        service.subscribe(7L, 40L);

        verify(repository).findByUserIdAndIdGreaterThanOrderByIdAsc(7L, 40L);
        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void initialSubscriptionCanReplayPersistedNotificationsWhenNoCursorExists() throws Exception {
        NotificationRepository repository = mock(NotificationRepository.class);
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationService service = new NotificationService(repository) {
            @Override
            SseEmitter createEmitter() {
                return emitter;
            }
        };
        Notification notification = new Notification();
        notification.setId(2L);
        notification.setUserId(7L);
        notification.setType("LISTING_UNAVAILABLE");
        notification.setTitle("Saved listing unavailable");
        notification.setMessage("The listing was sold.");
        when(repository.findByUserIdAndIdGreaterThanOrderByIdAsc(7L, 0L))
                .thenReturn(List.of(notification));

        service.subscribe(7L, 0L);

        verify(repository).findByUserIdAndIdGreaterThanOrderByIdAsc(7L, 0L);
        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
