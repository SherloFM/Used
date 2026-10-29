package com.example.Used.Service;

import com.example.Used.Model.Notification;
import com.example.Used.Repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTransactionTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsLiveEventOnlyAfterCommit() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = spy(new NotificationService(repository));
        Notification notification = new Notification();
        notification.setId(20L);
        when(repository.save(any(Notification.class))).thenReturn(notification);
        doNothing().when(service).publishToUser(anyLong(), anyString(), anyMap());
        TransactionSynchronizationManager.initSynchronization();

        service.sendToUser(7L, "LISTING_UPDATED", Map.of(
                "title", "Updated",
                "message", "The saved listing changed."));

        verify(service, never()).publishToUser(anyLong(), anyString(), anyMap());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                TransactionSynchronization::afterCommit);
        verify(service).publishToUser(eq(7L), eq("LISTING_UPDATED"), argThat(
                data -> Long.valueOf(20L).equals(data.get("notificationId"))));
    }

    @Test
    void doesNotSendLiveEventWhenTransactionRollsBack() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = spy(new NotificationService(repository));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(service).publishToUser(anyLong(), anyString(), anyMap());
        TransactionSynchronizationManager.initSynchronization();

        service.sendToUser(7L, "LISTING_UPDATED", Map.of(
                "title", "Updated",
                "message", "The saved listing changed."));

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(service, never()).publishToUser(anyLong(), anyString(), anyMap());
    }

    @Test
    void broadcastWaitsForTransactionCommit() throws Exception {
        NotificationRepository repository = mock(NotificationRepository.class);
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationService service = new NotificationService(repository) {
            @Override
            SseEmitter createEmitter() {
                return emitter;
            }
        };
        service.subscribe(7L, null);
        clearInvocations(emitter);
        TransactionSynchronizationManager.initSynchronization();

        service.broadcast("AUDIT", Map.of("message", "Committed"));

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(
                TransactionSynchronization::afterCommit);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }
}
