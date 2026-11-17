package com.example.notification.messaging;

import com.example.notification.model.ProcessedListingEvent;
import com.example.notification.repository.ProcessedListingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The database half of the duplicate-email fix. */
class ProcessedEventGuardTest {

    private ProcessedListingEventRepository repository;
    private ProcessedEventGuard guard;

    @BeforeEach
    void setup() {
        repository = mock(ProcessedListingEventRepository.class);
        guard = new ProcessedEventGuard(repository);
    }

    @Test
    void theFirstDeliveryClaimsTheEvent() {
        assertTrue(guard.claim("e1", "SOLD", 7L));
        verify(repository).saveAndFlush(any(ProcessedListingEvent.class));
    }

    @Test
    void aRedeliveredEventIsRefusedByThePrimaryKey() {
        when(repository.saveAndFlush(any(ProcessedListingEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertFalse(guard.claim("e1", "SOLD", 7L));
    }

    @Test
    void releasingAllowsTheRetryToRunAgain() {
        guard.release("e1");

        verify(repository).deleteById("e1");
    }

    @Test
    void aFailedReleaseIsSwallowedSoTheOriginalFailureStands() {
        doThrow(new IllegalStateException("database is down"))
                .when(repository).deleteById("e1");

        // Must not throw: the listener is already unwinding its own failure.
        guard.release("e1");

        verify(repository).deleteById("e1");
    }

    @Test
    void aClaimNeverDeletesAnything() {
        guard.claim("e1", "SOLD", 7L);

        verify(repository, never()).deleteById(any());
    }
}
