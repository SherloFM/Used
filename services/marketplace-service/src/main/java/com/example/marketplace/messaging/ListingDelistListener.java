package com.example.marketplace.messaging;

import com.example.marketplace.service.ListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes delist commands and reports the outcome back to Moderation.
 *
 * <p>The service call is idempotent, so redelivered or retried commands cannot
 * send a second email or write a second audit entry. The result is published
 * only after the service transaction has returned, which means it is published
 * only after the listing change committed.</p>
 */
@Component
public class ListingDelistListener {

    private static final Logger log = LoggerFactory.getLogger(ListingDelistListener.class);

    private final ListingService listingService;
    private final ListingDelistResultPublisher resultPublisher;

    public ListingDelistListener(ListingService listingService,
                                 ListingDelistResultPublisher resultPublisher) {
        this.listingService = listingService;
        this.resultPublisher = resultPublisher;
    }

    @RabbitListener(queues = RabbitTopology.DELIST_QUEUE)
    public void onDelistRequested(ListingDelistRequested command) {

        log.info("Delist requested for listing {} (report {})",
                command.listingId(), command.reportId());

        DelistOutcome outcome;
        String message;

        try {
            outcome = listingService.adminDeleteListingAs(
                    command.listingId(),
                    command.reason(),
                    command.adminId(),
                    command.adminUsername());
            message = describe(outcome);
        } catch (RuntimeException exception) {
            // A transient database or downstream failure. Reporting FAILED leaves
            // the report in DELISTING, and the Moderation retry sweep comes back.
            log.error("Delisting listing {} for report {} failed",
                    command.listingId(), command.reportId(), exception);
            outcome = DelistOutcome.FAILED;
            message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
        }

        ListingDelistResult result =
                new ListingDelistResult(command.reportId(), outcome, message);

        if (!resultPublisher.publish(result)) {
            // The listing change may already be committed, so the result must not
            // be lost. Rejecting this command is safe: the retry will hit the
            // idempotent path and produce ALREADY_DELISTED.
            throw new IllegalStateException(
                    "Could not publish the delist result for report " + command.reportId());
        }
    }

    private String describe(DelistOutcome outcome) {
        return switch (outcome) {
            case DELISTED -> "Listing delisted.";
            case ALREADY_DELISTED -> "Listing was already delisted.";
            case NOT_FOUND -> "Listing not found.";
            case SOLD -> "The listing is sold and cannot be delisted.";
            case FAILED -> "The delist did not complete.";
        };
    }
}
