package com.example.notification.repository;

import com.example.notification.model.ProcessedListingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedListingEventRepository
        extends JpaRepository<ProcessedListingEvent, String> {
}
