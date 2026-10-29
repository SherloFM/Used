package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.RateLimitException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.ListingReport;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Requests.ResolveReportRequest;
import com.example.Used.Model.Requests.SubmitReportRequest;
import com.example.Used.Model.Responses.ReportResponse;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingReportRepository;
import com.example.Used.Repository.ListingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {

    /** How many reports one user may submit per day. */
    private static final int MAX_REPORTS_PER_DAY = 5;

    private final ListingReportRepository reportRepository;
    private final ListingRepository listingRepository;
    private final CurrentUserService currentUserService;
    private final ListingService listingService;

    public ReportService(ListingReportRepository reportRepository,
                         ListingRepository listingRepository,
                         CurrentUserService currentUserService,
                         ListingService listingService) {
        this.reportRepository = reportRepository;
        this.listingRepository = listingRepository;
        this.currentUserService = currentUserService;
        this.listingService = listingService;
    }

    // ==========================================
    // USER FEATURES
    // ==========================================

    @Transactional
    public ReportResponse submitReport(SubmitReportRequest request) {

        User reporter = currentUserService.getCurrentUser();

        // The listing must exist (even if it is sold or delisted - the report
        // still has to be possible, admins just cannot delist those).
        Listings listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        // A user cannot report their own listing
        if (listing.getUser().getId().equals(reporter.getId())) {
            throw new InformationExistException("You cannot report your own listing");
        }

        // Light anti-abuse rate limit
        long todaysReports = reportRepository.countByReporterIdAndCreatedAtAfter(
                reporter.getId(), LocalDateTime.now().minusDays(1));
        if (todaysReports >= MAX_REPORTS_PER_DAY) {
            throw new RateLimitException(
                    "You have submitted too many reports today. Please try again tomorrow.");
        }

        // Duplicate policy: one open report per user per listing.
        // A DISMISSED report does not block a new one.
        boolean hasOpenReport = reportRepository.existsByListingIdAndReporterIdAndStatusIn(
                listing.getId(),
                reporter.getId(),
                List.of(ListingReport.Status.PENDING, ListingReport.Status.UNDER_REVIEW));
        if (hasOpenReport) {
            throw new InformationExistException(
                    "You have already reported this listing and it is being reviewed");
        }

        ListingReport report = new ListingReport();
        report.setListing(listing);
        report.setReporter(reporter);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setStatus(ListingReport.Status.PENDING);

        return ReportResponse.from(reportRepository.save(report), false);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMyReports(int page, int size) {
        User reporter = currentUserService.getCurrentUser();
        return reportRepository
                .findByReporterIdOrderByCreatedAtDesc(reporter.getId(), PageRequest.of(page, size))
                .map(report -> ReportResponse.from(report, false));
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

        return result.map(report -> ReportResponse.from(report, true));
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long id) {
        ensureAdmin();
        return ReportResponse.from(findReport(id), true);
    }

    /** Previous reports for the same listing, so admins see the history. */
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsForListing(Long listingId) {
        ensureAdmin();
        return reportRepository.findByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(report -> ReportResponse.from(report, true))
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
        return ReportResponse.from(reportRepository.save(report), true);
    }

    @Transactional
    public ReportResponse resolveReport(Long id, ResolveReportRequest request) {

        User admin = ensureAdmin();

        ListingReport report = findReport(id);
        if (report.getStatus() == ListingReport.Status.DISMISSED
                || report.getStatus() == ListingReport.Status.ACTION_TAKEN) {
            throw new InformationExistException("This report has already been resolved");
        }
        if (request.getDecision() != ListingReport.Status.DISMISSED
                && request.getDecision() != ListingReport.Status.ACTION_TAKEN) {
            throw new InformationExistException(
                    "Decision must be DISMISSED or ACTION_TAKEN");
        }

        report.setStatus(request.getDecision());
        report.setAdmin(admin);
        report.setAdminNotes(request.getAdminNotes());
        report.setResolvedAt(LocalDateTime.now());

        // Taking action attempts to delist through the EXISTING listing service,
        // which handles the seller email, audit log, and sold-listing refusal.
        // If delisting fails (e.g. the listing was already sold), the report is
        // still resolved - we just record why in the notes.
        if (request.getDecision() == ListingReport.Status.ACTION_TAKEN
                && request.isDelistListing()) {
            try {
                listingService.adminDeleteListing(
                        report.getListing().getId(),
                        "Removed by moderation (report #" + report.getId() + ")");
                report.setAdminNotes(appendNote(report.getAdminNotes(), "Listing delisted."));
            } catch (InformationExistException e) {
                report.setAdminNotes(appendNote(report.getAdminNotes(),
                        "Listing not delisted: " + e.getMessage()));
            }
        }

        return ReportResponse.from(reportRepository.save(report), true);
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private ListingReport findReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private User ensureAdmin() {
        User user = currentUserService.getCurrentUser();
        if (user.getRole() != User.Role.ADMIN) {
            // Backend-enforced; hiding buttons is not sufficient
            throw new ResourceNotFoundException("Report not found");
        }
        return user;
    }

    private String appendNote(String notes, String addendum) {
        if (notes == null || notes.isBlank()) return addendum;
        return notes + " " + addendum;
    }
}
