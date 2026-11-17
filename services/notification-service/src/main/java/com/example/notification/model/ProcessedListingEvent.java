package com.example.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One row per listing event this service has finished handling.
 *
 * <p>This is the idempotency record that stops a redelivered event from sending
 * a second "your item sold" email. The event id is the primary key, so a race
 * between two consumers is resolved by the database, not by luck.</p>
 *
 * <p>Rows are small and bounded by traffic. Nothing depends on their longevity,
 * so an occasional purge of old rows is safe.</p>
 */
@Entity
@Table(name = "processed_listing_events")
public class ProcessedListingEvent {

    /** The eventId from the message: also the AMQP messageId. */
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedListingEvent() {
    }

    public ProcessedListingEvent(String eventId, String eventType, Long listingId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.listingId = listingId;
        this.processedAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getListingId() {
        return listingId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
