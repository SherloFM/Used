package com.example.notification.messaging;

import com.example.notification.model.ProcessedListingEvent;
import com.example.notification.repository.ProcessedListingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims an event before its notifications are sent, so a redelivered message
 * cannot email the seller twice.
 *
 * <p>The claim runs in its own transaction (REQUIRES_NEW) for two reasons: it
 * must be committed BEFORE the email goes out to actually prevent a duplicate,
 * and a failed insert must not poison the caller's transaction.</p>
 *
 * <p>If handling fails after the claim, {@link #release} removes it so the
 * retry can run. The remaining window is a crash between the claim commit and
 * the email: the email would then be skipped rather than repeated. That is the
 * deliberate trade: for "your item sold", a missing email is recoverable by the
 * seller checking the app; a duplicate email is not.</p>
 */
@Component
public class ProcessedEventGuard {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);

    private final ProcessedListingEventRepository repository;

    public ProcessedEventGuard(ProcessedListingEventRepository repository) {
        this.repository = repository;
    }

    /** @return true if this delivery owns the event; false if it was already handled. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String eventId, String eventType, Long listingId) {

        try {
            repository.saveAndFlush(new ProcessedListingEvent(eventId, eventType, listingId));
            return true;
        } catch (DataIntegrityViolationException alreadyClaimed) {
            // The primary key is the event id: someone else got here first.
            return false;
        }
    }

    /** Gives the event back so the retry is not mistaken for a duplicate. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String eventId) {
        try {
            repository.deleteById(eventId);
        } catch (RuntimeException exception) {
            log.warn("Could not release the claim on event {}; the retry will skip it",
                    eventId, exception);
        }
    }
}
