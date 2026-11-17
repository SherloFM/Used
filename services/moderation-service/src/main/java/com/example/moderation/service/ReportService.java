package com.example.moderation.service;

import com.example.moderation.client.AccountsClient;
import com.example.moderation.client.ListingSummary;
import com.example.moderation.client.MarketplaceClient;
import com.example.moderation.exceptions.InformationExistException;
import com.example.moderation.exceptions.RateLimitException;
import com.example.moderation.exceptions.ResourceNotFoundException;
import com.example.moderation.messaging.DelistOutcome;
import com.example.moderation.messaging.ListingDelistRequested;
import com.example.moderation.messaging.ListingDelistResult;
import com.example.moderation.model.ListingReport;
import com.example.moderation.model.requests.ResolveReportRequest;
import com.example.moderation.model.requests.SubmitReportRequest;
import com.example.moderation.model.responses.ReportResponse;
import com.example.moderation.repository.ListingReportRepository;
import com.example.moderation.security.CurrentActor;
import com.example.moderation.security.CurrentActorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** How many reports one user may submit per day. */
    private static final int MAX_REPORTS_PER_DAY = 5;
    private static final String MISSING_LISTING = "Unavailable listing";
    private static final String MISSING_USER = "Deleted user";

    private final ListingReportRepository reportRepository;
    private final MarketplaceClient marketplaceClient;
    private final AccountsClient accountsClient;
    private final CurrentActorProvider currentActorProvider;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ReportService(ListingReportRepository reportRepository,
                         MarketplaceClient marketplaceClient,
                         AccountsClient accountsClient,
                         CurrentActorProvider currentActorProvider,
                         ApplicationEventPublisher applicationEventPublisher) {
        this.reportRepository = reportRepository;
        this.marketplaceClient = marketplaceClient;
        this.accountsClient = accountsClient;
        this.currentActorProvider = currentActorProvider;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    // ==========================================
    // USER FEATURES
    // ==========================================

    @Transactional
    public ReportResponse submitReport(SubmitReportRequest request) {

        CurrentActor reporter = currentActorProvider.get();

        // The listing must exist (even if it is sold or delisted - the report
        // still has to be possible, admins just cannot delist those).
        ListingSummary listing = marketplaceClient.find(request.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        // A user cannot report their own listing
        if (reporter.id().equals(listing.sellerId())) {
            throw new InformationExistException("You cannot report your own listing");
        }

        // Light anti-abuse rate limit
        long todaysReports = reportRepository.countByReporterIdAndCreatedAtAfter(
                reporter.id(), LocalDateTime.now().minusDays(1));
        if (todaysReports >= MAX_REPORTS_PER_DAY) {
            throw new RateLimitException(
                    "You have submitted too many reports today. Please try again tomorrow.");
        }

        // Duplicate policy: one open report per user per listing.
        // A DISMISSED report does not block a new one.
        boolean hasOpenReport = reportRepository.existsByListingIdAndReporterIdAndStatusIn(
                listing.id(),
                reporter.id(),
                List.of(ListingReport.Status.PENDING, ListingReport.Status.UNDER_REVIEW));
        if (hasOpenReport) {
            throw new InformationExistException(
                    "You have already reported this listing and it is being reviewed");
        }

        ListingReport report = new ListingReport();
        report.setListingId(listing.id());
        report.setReporterId(reporter.id());
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setStatus(ListingReport.Status.PENDING);

        ListingReport saved = reportRepository.save(report);
        return ReportResponse.from(saved, false, listing.title(), usernameOf(reporter));
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMyReports(int page, int size) {
        CurrentActor reporter = currentActorProvider.get();
        Map<Long, String> titles = new HashMap<>();
        String reporterUsername = usernameOf(reporter);
        return reportRepository
                .findByReporterIdOrderByCreatedAtDesc(reporter.id(), PageRequest.of(page, size))
                .map(report -> ReportResponse.from(report, false,
                        titleOf(report.getListingId(), titles), reporterUsername));
    }

    // ==========================================
    // ADMIN FEATURES
    // ==========================================

    /**
     * Lists reports for admins with an optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<ReportResponse> getAllReports(ListingReport.Status status, int page, int size) {
        ensureAdmin();
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageable = PageRequest.of(page, size, newestFirst);

        Page<ListingReport> result = (status == null)
                ? reportRepository.findAll(pageable)
                : reportRepository.findByStatus(status, pageable);

        Map<Long, String> titles = new HashMap<>();
        Map<Long, String> usernames = new HashMap<>();
        return result.map(report -> adminResponse(report, titles, usernames));
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long id) {
        ensureAdmin();
        return adminResponse(findReport(id), new HashMap<>(), new HashMap<>());
    }

    /** Previous reports for the same listing, so admins see the history. */
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsForListing(Long listingId) {
        ensureAdmin();
        Map<Long, String> titles = new HashMap<>();
        Map<Long, String> usernames = new HashMap<>();
        return reportRepository.findByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(report -> adminResponse(report, titles, usernames))
                .toList();
    }

    @Transactional
    public ReportResponse markUnderReview(Long id) {
        ensureAdmin();

        ListingReport report = findReport(id);
        if (report.getStatus() != ListingReport.Status.PENDING) {
            throw new InformationExistException(
                    "Only pending reports can be moved to review");
        }

        report.setStatus(ListingReport.Status.UNDER_REVIEW);
        return adminResponse(reportRepository.save(report), new HashMap<>(), new HashMap<>());
    }

    /**
     * Resolves a report. When the decision is ACTION_TAKEN and delisting is
     * requested, the report moves to DELISTING and a delist command is published
     * only after this transaction commits. Marketplace performs the delist and
     * reports back; until then the report stays DELISTING and the retry sweep
     * keeps the two services converging.
     *
     * <p>Resolving here no longer depends on Marketplace being reachable. The
     * admin's decision is committed first, which is the whole point of the saga.</p>
     */
    @Transactional
    public ReportResponse resolveReport(Long id, ResolveReportRequest request) {

        CurrentActor admin = ensureAdmin();

        ListingReport report = findReport(id);
        if (report.getStatus() == ListingReport.Status.DISMISSED
                || report.getStatus() == ListingReport.Status.ACTION_TAKEN
                || report.getStatus() == ListingReport.Status.DELISTING) {
            throw new InformationExistException("This report has already been resolved");
        }
        if (request.getDecision() != ListingReport.Status.DISMISSED
                && request.getDecision() != ListingReport.Status.ACTION_TAKEN) {
            throw new InformationExistException(
                    "Decision must be DISMISSED or ACTION_TAKEN");
        }

        boolean delist = request.getDecision() == ListingReport.Status.ACTION_TAKEN
                && request.isDelistListing();

        report.setAdminId(admin.id());
        report.setAdminNotes(request.getAdminNotes());

        if (delist) {
            // Not resolved yet: Marketplace has not confirmed the delist.
            report.setStatus(ListingReport.Status.DELISTING);
            report.setResolvedAt(null);
        } else {
            report.setStatus(request.getDecision());
            report.setResolvedAt(LocalDateTime.now());
        }

        ListingReport saved = reportRepository.save(report);

        if (delist) {
            // A Spring event, delivered to the dispatcher only after commit, so a
            // rolled-back decision can never send a delist command.
            applicationEventPublisher.publishEvent(new ListingDelistRequested(
                    saved.getId(),
                    saved.getListingId(),
                    admin.id(),
                    usernameOf(admin),
                    delistReason(saved)));
        }

        return adminResponse(saved, new HashMap<>(), new HashMap<>());
    }

    /**
     * Applies the outcome Marketplace reported. Idempotent: duplicate results and
     * reports that already moved on are ignored. A FAILED outcome leaves the
     * report in DELISTING so the retry sweep tries again.
     */
    @Transactional
    public void applyDelistResult(ListingDelistResult result) {

        ListingReport report = reportRepository.findById(result.reportId()).orElse(null);
        if (report == null) {
            log.warn("Ignoring delist result for report {}: report does not exist",
                    result.reportId());
            return;
        }
        if (report.getStatus() != ListingReport.Status.DELISTING) {
            log.info("Ignoring delist result for report {}: status is {}",
                    result.reportId(), report.getStatus());
            return;
        }
        if (result.outcome() == DelistOutcome.FAILED) {
            log.warn("Delist of listing {} for report {} failed; it stays DELISTING",
                    report.getListingId(), report.getId());
            return;
        }

        report.setStatus(ListingReport.Status.ACTION_TAKEN);
        report.setResolvedAt(LocalDateTime.now());
        report.setAdminNotes(appendNote(report.getAdminNotes(), noteFor(result.outcome())));
        reportRepository.save(report);
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private ListingReport findReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private CurrentActor ensureAdmin() {
        CurrentActor actor = currentActorProvider.get();
        if (!actor.isAdmin()) {
            // Backend-enforced; hiding buttons is not sufficient
            throw new ResourceNotFoundException("Report not found");
        }
        return actor;
    }

    private ReportResponse adminResponse(ListingReport report,
                                         Map<Long, String> titles,
                                         Map<Long, String> usernames) {
        return ReportResponse.from(report, true,
                titleOf(report.getListingId(), titles),
                displayUsername(report.getReporterId(), usernames));
    }

    private String titleOf(Long listingId, Map<Long, String> cache) {
        return cache.computeIfAbsent(listingId, id -> marketplaceClient.find(id)
                .map(ListingSummary::title)
                .filter(title -> title != null && !title.isBlank())
                .orElse(MISSING_LISTING));
    }

    private String displayUsername(Long userId, Map<Long, String> cache) {
        return cache.computeIfAbsent(userId, id -> accountsClient.find(id)
                .map(user -> user.username() != null ? user.username() : user.email())
                .filter(name -> name != null && !name.isBlank())
                .orElse(MISSING_USER));
    }

    private String usernameOf(CurrentActor actor) {
        if (actor.username() != null && !actor.username().isBlank()) {
            return actor.username();
        }
        return displayUsername(actor.id(), new HashMap<>());
    }

    private String appendNote(String notes, String addendum) {
        if (notes == null || notes.isBlank()) return addendum;
        return notes + " " + addendum;
    }

    private String delistReason(ListingReport report) {
        return "Removed by moderation (report #" + report.getId() + ")";
    }

    private String noteFor(DelistOutcome outcome) {
        return switch (outcome) {
            case DELISTED -> "Listing delisted.";
            case ALREADY_DELISTED -> "Listing was already delisted.";
            case NOT_FOUND -> "Listing not delisted: listing not found.";
            case SOLD -> "Listing not delisted: the listing is sold.";
            case FAILED -> "Listing not delisted yet.";
        };
    }
}
