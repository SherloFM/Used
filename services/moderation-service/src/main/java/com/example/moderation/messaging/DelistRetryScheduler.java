package com.example.moderation.messaging;

import com.example.moderation.client.AccountsClient;
import com.example.moderation.model.ListingReport;
import com.example.moderation.repository.ListingReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Re-sends delist commands for reports that have been stuck in DELISTING.
 *
 * <p>This is the real retry loop of the saga: the dead-letter queue is only a
 * place to inspect failures. Every sweep is at-least-once, and Marketplace's
 * idempotent delist makes duplicate commands harmless.</p>
 */
@Component
public class DelistRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DelistRetryScheduler.class);

    private final ListingReportRepository reportRepository;
    private final AccountsClient accountsClient;
    private final ListingCommandPublisher publisher;
    private final long staleAfterMillis;
    private final int batchSize;

    public DelistRetryScheduler(ListingReportRepository reportRepository,
                                AccountsClient accountsClient,
                                ListingCommandPublisher publisher,
                                @Value("${delist.retry.stale-after-ms:60000}") long staleAfterMillis,
                                @Value("${delist.retry.batch-size:100}") int batchSize) {
        this.reportRepository = reportRepository;
        this.accountsClient = accountsClient;
        this.publisher = publisher;
        this.staleAfterMillis = staleAfterMillis;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${delist.retry.interval-ms:60000}")
    public void republishStaleDelists() {

        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(staleAfterMillis));

        List<ListingReport> stale;
        try {
            stale = reportRepository.findByStatusAndUpdatedAtBefore(
                    ListingReport.Status.DELISTING, cutoff, PageRequest.of(0, batchSize));
        } catch (RuntimeException exception) {
            // The database may be restarting; the next sweep will try again.
            log.error("Could not read reports stuck in DELISTING", exception);
            return;
        }

        if (stale.isEmpty()) {
            return;
        }

        log.info("Republishing {} delist command(s) stuck in DELISTING", stale.size());

        for (ListingReport report : stale) {
            if (report.getAdminId() == null) {
                log.error("Report {} is DELISTING without an admin id; skipping", report.getId());
                continue;
            }
            if (!publisher.requestDelist(commandFor(report))) {
                log.warn("Delist command for report {} is still unconfirmed; will retry",
                        report.getId());
            }
        }
    }

    private ListingDelistRequested commandFor(ListingReport report) {
        return new ListingDelistRequested(
                report.getId(),
                report.getListingId(),
                report.getAdminId(),
                adminUsernameOf(report.getAdminId()),
                "Removed by moderation (report #" + report.getId() + ")");
    }

    /**
     * The report only stores the admin id, so the username is resolved again.
     * A deleted or unavailable account falls back to "admin", which keeps the
     * Marketplace audit entry usable instead of failing the retry.
     */
    private String adminUsernameOf(Long adminId) {
        try {
            return accountsClient.find(adminId)
                    .map(user -> user.username() != null && !user.username().isBlank()
                            ? user.username()
                            : user.email())
                    .filter(name -> name != null && !name.isBlank())
                    .orElse("admin");
        } catch (RuntimeException exception) {
            log.warn("Could not look up admin {}; using the fallback name", adminId, exception);
            return "admin";
        }
    }
}
