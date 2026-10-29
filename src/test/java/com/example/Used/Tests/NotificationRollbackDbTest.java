package com.example.Used.Tests;

import com.example.Used.Repository.NotificationRepository;
import com.example.Used.Model.Notification;
import com.example.Used.Service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_rollback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Import(NotificationService.class)
class NotificationRollbackDbTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void notificationInsertRollsBackWithEnclosingTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            notificationService.sendToUser(77L, "LISTING_UPDATED", Map.of(
                    "title", "A saved listing was updated",
                    "message", "Price changed.",
                    "type", "info",
                    "listingId", 42L));
            throw new IllegalStateException("force rollback");
        }));

        assertEquals(0, notificationRepository.count());
    }

    @Test
    void offlineNotificationsCanBeReplayedAfterTheLastSeenId() {
        Notification lastSeen = notification(77L, "Already seen");
        Notification missed = notification(77L, "Missed while offline");
        Notification otherUser = notification(88L, "Belongs to another user");
        notificationRepository.saveAllAndFlush(List.of(lastSeen, missed, otherUser));

        List<Notification> replay = notificationRepository
                .findByUserIdAndIdGreaterThanOrderByIdAsc(77L, lastSeen.getId());

        assertEquals(List.of(missed.getId()), replay.stream().map(Notification::getId).toList());
    }

    private static Notification notification(Long userId, String title) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("LISTING_UPDATED");
        notification.setTitle(title);
        notification.setMessage(title);
        return notification;
    }
}
