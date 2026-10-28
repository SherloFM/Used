package com.example.Used;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Exceptions.RateLimitException;
import com.example.Used.Exceptions.ResourceNotFoundException;
import com.example.Used.Model.ListingReport;
import com.example.Used.Model.Listings;
import com.example.Used.Model.Requests.ResolveReportRequest;
import com.example.Used.Model.Requests.SubmitReportRequest;
import com.example.Used.Model.Responses.ReportResponse;
import com.example.Used.Model.User;
import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.ListingReportRepository;
import com.example.Used.Repository.UserRepository;
import com.example.Used.Security.MyUserDetails;
import com.example.Used.Service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database tests for the listing report system.
 * These run against the REAL project database (profile "dev").
 *
 * Every test method is @Transactional, so anything a test writes
 * (reports, fixtures) is rolled back when the test ends - the real
 * marketplace data is never changed by a test run.
 *
 * The current user is placed into the Spring Security context the same
 * way the JWT filter does it, so CurrentUserService behaves exactly
 * like in production.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ReportServiceDbTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ListingReportRepository reportRepository;

    // Real seed data loaded from the database
    private User buyer;      // carol_buyer - owns no listings
    private User admin;      // admin      - role ADMIN

    private Listings activeListing;  // "Canon EOS M50 Camera" by bob_seller (@used.local)
    private Listings soldListing;    // "MacBook Pro 13 inch 2020" by alice_seller (@used.local)

    @BeforeEach
    void setUp() {
        // Load real seed data by email
        buyer = userRepository.findByEmail("carol@used.local");
        admin = userRepository.findByEmail("admin@test.com");

        activeListing = listingRepository.findById(34L)
                .orElseThrow(() -> new AssertionError(
                        "Seed listing 34 missing - run seed.sql first"));
        soldListing = listingRepository.findById(31L)
                .orElseThrow(() -> new AssertionError(
                        "Seed listing 31 missing - run seed.sql first"));

        loginAs(buyer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Places a REAL database user into the security context (no mocks). */
    private void loginAs(User user) {
        MyUserDetails details = new MyUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private SubmitReportRequest reportListing(Long listingId, ListingReport.Reason reason) {
        SubmitReportRequest request = new SubmitReportRequest();
        request.setListingId(listingId);
        request.setReason(reason);
        request.setDescription("Test report");
        return request;
    }

    // ==========================================
    // SUBMISSION RULES
    // ==========================================

    @Test
    void aUserCanReportAnActiveListingAndItStartsPending() {
        ReportResponse report = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.SUSPECTED_SCAM));

        assertEquals(ListingReport.Status.PENDING, report.getStatus());
        assertEquals(activeListing.getId(), report.getListingId());
        assertEquals(buyer.getId(), report.getReporterId());
        assertNotNull(report.getCreatedAt());
        // User-facing responses never carry internal admin fields
        assertNull(report.getAdminNotes());
        assertNull(report.getAdminId());
    }

    @Test
    void aUserCannotReportTheirOwnListing() {
        // Create a listing owned by the reporter itself (rolled back after the test)
        Listings ownListing = new Listings();
        ownListing.setTitle("Carol's Test Item");
        ownListing.setDescription("Not a real listing");
        ownListing.setPrice(10.0);
        ownListing.setCondition("Used");
        ownListing.setLocation("Manama");
        ownListing.setStatus(Listings.Status.ACTIVE);
        ownListing.setUser(buyer);
        Listings savedOwnListing = listingRepository.save(ownListing);

        InformationExistException thrown = assertThrows(
                InformationExistException.class,
                () -> reportService.submitReport(
                        reportListing(savedOwnListing.getId(), ListingReport.Reason.OTHER)));
        assertEquals("You cannot report your own listing", thrown.getMessage());

        // The listing row was created, but no report row was ever saved
        for (ListingReport saved : reportRepository.findAll()) {
            assertNotEquals(savedOwnListing.getId(), saved.getListing().getId());
        }
    }

    @Test
    void aNonexistentListingCannotBeReported() {
        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.submitReport(
                        reportListing(9_999_999L, ListingReport.Reason.OTHER)));
        assertEquals("Listing not found", thrown.getMessage());
    }

    @Test
    void duplicateOpenReportsAreRejectedButDismissedOnesAllowANewReport() {
        // First report succeeds
        ReportResponse first = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.OTHER));

        // A second open report by the same user for the same listing is rejected
        InformationExistException thrown = assertThrows(
                InformationExistException.class,
                () -> reportService.submitReport(
                        reportListing(activeListing.getId(), ListingReport.Reason.PROHIBITED_ITEM)));
        assertTrue(thrown.getMessage().contains("already reported this listing"));

        // Simulate the earlier report being resolved to DISMISSED
        ListingReport stored = reportRepository.findById(first.getId()).orElseThrow();
        stored.setStatus(ListingReport.Status.DISMISSED);
        reportRepository.save(stored);

        // After dismissal the same user may report the same listing again
        ReportResponse newReport = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.PROHIBITED_ITEM));
        assertEquals(ListingReport.Status.PENDING, newReport.getStatus());
        assertNotEquals(first.getId(), newReport.getId());
    }

    @Test
    void moreThanFiveReportsInADayIsRateLimited() {
        // Five reports already exist for this user within the last day
        for (int i = 0; i < 5; i++) {
            ListingReport existing = new ListingReport();
            existing.setListing(activeListing);
            existing.setReporter(buyer);
            existing.setReason(ListingReport.Reason.OTHER);
            existing.setStatus(ListingReport.Status.DISMISSED); // old resolved reports still count as activity
            reportRepository.save(existing);
        }

        RateLimitException thrown = assertThrows(
                RateLimitException.class,
                () -> reportService.submitReport(
                        reportListing(activeListing.getId(), ListingReport.Reason.OTHER)));
        assertTrue(thrown.getMessage().contains("too many reports today"));
    }

    // ==========================================
    // ADMIN WORKFLOW
    // ==========================================

    @Test
    void aRegularUserCannotUseAdminActions() {
        // buyer (USER role) is still the current user here
        assertThrows(Exception.class, () -> reportService.getAllReports(null, 0, 20));
        assertThrows(Exception.class, () -> reportService.getReportsForListing(34L));
        assertThrows(Exception.class, () -> reportService.markUnderReview(1L));
        assertThrows(Exception.class, () -> reportService.resolveReport(1L, new ResolveReportRequest()));
    }

    @Test
    void adminCanReviewPendingReportsAndMoveThemUnderReview() {
        ReportResponse submitted = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.SUSPECTED_SCAM));

        loginAs(admin);

        // The admin view of the same report exposes the internal fields
        ReportResponse fromAdminView = reportService.getReport(submitted.getId());
        assertEquals(submitted.getId(), fromAdminView.getId());
        assertNull(fromAdminView.getAdminNotes()); // no notes yet, but the field is visible to admins

        ReportResponse underReview = reportService.markUnderReview(submitted.getId());
        assertEquals(ListingReport.Status.UNDER_REVIEW, underReview.getStatus());

        // Status filter returns it
        assertTrue(reportService.getAllReports(ListingReport.Status.UNDER_REVIEW, 0, 20)
                .getContent().stream()
                .anyMatch(r -> r.getId().equals(submitted.getId())));
    }

    @Test
    void resolvedReportsCannotBeReviewedOrResolvedAgain() {
        ReportResponse submitted = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.OTHER));
        loginAs(admin);
        reportService.markUnderReview(submitted.getId());

        ResolveReportRequest request = new ResolveReportRequest();
        request.setDecision(ListingReport.Status.DISMISSED);
        ReportResponse resolved = reportService.resolveReport(submitted.getId(), request);
        assertEquals(ListingReport.Status.DISMISSED, resolved.getStatus());

        InformationExistException thrown = assertThrows(
                InformationExistException.class,
                () -> reportService.markUnderReview(submitted.getId()));
        assertEquals("Only pending reports can be moved to review", thrown.getMessage());

        ResolveReportRequest again = new ResolveReportRequest();
        again.setDecision(ListingReport.Status.ACTION_TAKEN);
        assertThrows(InformationExistException.class,
                () -> reportService.resolveReport(submitted.getId(), again));
    }

    @Test
    void adminDismissalRecordsTheDecisionAndTimestamps() {
        ReportResponse submitted = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.INAPPROPRIATE_CONTENT));
        loginAs(admin);

        ResolveReportRequest request = new ResolveReportRequest();
        request.setDecision(ListingReport.Status.DISMISSED);
        request.setAdminNotes("Price and condition look fine.");

        ReportResponse resolved = reportService.resolveReport(submitted.getId(), request);

        assertEquals(ListingReport.Status.DISMISSED, resolved.getStatus());
        assertEquals(admin.getId(), resolved.getAdminId());
        assertEquals("Price and condition look fine.", resolved.getAdminNotes());
        assertNotNull(resolved.getResolvedAt());
    }

    /**
     * Delisting goes through the EXISTING listing service.
     * The seller here is the seed account bob@used.local (a fake mailbox),
     * so the delisting email goes to the test seed data, not to a person.
     */
    @Test
    void delistingThroughReportResolutionUsesTheExistingListingService() {
        ReportResponse submitted = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.PROHIBITED_ITEM));
        loginAs(admin);
        reportService.markUnderReview(submitted.getId());

        ResolveReportRequest request = new ResolveReportRequest();
        request.setDecision(ListingReport.Status.ACTION_TAKEN);
        request.setAdminNotes("Prohibited item confirmed.");
        request.setDelistListing(true);

        ReportResponse resolved = reportService.resolveReport(submitted.getId(), request);

        // The delisting really happened on the listing row itself
        Listings reloaded = listingRepository.findById(activeListing.getId()).orElseThrow();
        assertEquals(Listings.Status.DELISTED, reloaded.getStatus());
        assertEquals(ListingReport.Status.ACTION_TAKEN, resolved.getStatus());
        assertTrue(resolved.getAdminNotes().contains("Listing delisted."));
        assertNotNull(resolved.getResolvedAt());
    }

    /**
     * A SOLD listing cannot be delisted (existing listing service rule),
     * but the report itself is still resolved and the failure noted.
     */
    @Test
    void actionTakenOnAnAlreadySoldListingStillRecordsTheDecision() {
        ReportResponse submitted = reportService.submitReport(
                reportListing(soldListing.getId(), ListingReport.Reason.MISLEADING_LISTING));
        loginAs(admin);

        ResolveReportRequest request = new ResolveReportRequest();
        request.setDecision(ListingReport.Status.ACTION_TAKEN);
        request.setDelistListing(true);

        ReportResponse resolved = reportService.resolveReport(submitted.getId(), request);

        assertEquals(ListingReport.Status.ACTION_TAKEN, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());
        assertTrue(resolved.getAdminNotes().contains("not delisted"));

        // The sold listing was never changed
        Listings reloaded = listingRepository.findById(soldListing.getId()).orElseThrow();
        assertEquals(Listings.Status.SOLD, reloaded.getStatus());
    }

    // ==========================================
    // OWN REPORTS VIEW
    // ==========================================

    @Test
    void myReportsShowsOnlyMyReportsAndHidesInternalNotes() {
        ReportResponse mine = reportService.submitReport(
                reportListing(activeListing.getId(), ListingReport.Reason.OTHER));

        // Another user's report for the same listing must not leak into my view
        loginAs(userRepository.findByEmail("dan@used.local"));
        reportService.submitReport(reportListing(activeListing.getId(), ListingReport.Reason.OTHER));

        loginAs(buyer);
        List<ReportResponse> myReports = reportService.getMyReports(0, 20).getContent();

        assertEquals(1, myReports.size());
        assertEquals(mine.getId(), myReports.get(0).getId());
        // Internal fields are never exposed on user-facing responses
        assertTrue(myReports.stream()
                .allMatch(r -> r.getAdminNotes() == null && r.getAdminId() == null));
    }
}
