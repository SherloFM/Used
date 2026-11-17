package com.example.favorites.messaging;

import com.example.favorites.service.FavoriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives listing lifecycle facts and applies them to saved listings.
 *
 * <p>A rejected message is retried and then dead-lettered; it is never dropped
 * silently. Redelivery is safe because the removal path deletes the favorites,
 * so a second delivery finds nothing to do.</p>
 */
@Component
public class ListingEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ListingEventsListener.class);

    private final FavoriteService favoriteService;

    public ListingEventsListener(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @RabbitListener(queues = RabbitTopology.LISTING_EVENTS_QUEUE)
    public void onListingEvent(ListingEvent event) {

        log.info("Listing event {} ({}) for listing {}",
                event.eventId(), event.type(), event.listingId());

        switch (event.type()) {
            case UPDATED -> favoriteService.applyListingUpdated(event);
            case SOLD, DELISTED -> favoriteService.applyListingRemoved(event);
            case CREATED -> {
                // Nothing to do: nobody can have saved a listing at creation.
            }
        }
    }
}
