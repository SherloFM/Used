package com.example.moderation.service;

import com.example.moderation.client.AccountsClient;
import com.example.moderation.client.ListingSummary;
import com.example.moderation.client.MarketplaceClient;
import com.example.moderation.client.UserSummary;
import com.example.moderation.exceptions.InformationExistException;
import com.example.moderation.exceptions.RateLimitException;
import com.example.moderation.exceptions.ResourceNotFoundException;
import com.example.moderation.messaging.DelistOutcome;
import com.example.moderation.messaging.ListingDelistRequested;
import com.example.moderation.messaging.ListingDelistResult;
import com.example.moderation.model.ListingReport;
import com.example.moderation.model.requests.ResolveReportRequest;
import com.example.moderation.model.requests.SubmitReportRequest;
import com.example.moderation.repository.ListingReportRepository;
import com.example.moderation.security.CurrentActor;
import com.example.moderation.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private ListingReportRepository reports;
    private MarketplaceClient marketplace;
    private AccountsClient accounts;
    private CurrentActorProvider actors;
    private ApplicationEventPublisher events;
    private ReportService service;

    @BeforeEach
    void setup() {
        reports = mock(ListingReportRepository.class);
        marketplace = mock(MarketplaceClient.class);
        accounts = mock(AccountsClient.class);
        actors = mock(CurrentActorProvider.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ReportService(reports, marketplace, accounts, actors, events);
    }

    private void asUser(long id) {
        when(actors.get()).thenReturn(new CurrentActor(id, "alice", "USER"));
    }

    private void asAdmin() {
        when(actors.get()).thenReturn(new CurrentActor(99L, "admin", "ADMIN"));
    }

    private SubmitReportRequest submit(Long listingId) {
        SubmitReportRequest request = new SubmitReportRequest();
        request.setListingId(listingId);
        request.setReason(ListingReport.Reason.SUSPECTED_SCAM);
        request.setDescription("Looks fake");
        return request;
    }

    private ResolveReportRequest resolve(ListingReport.Status decision, boolean delist) {
        ResolveReportRequest request = new ResolveReportRequest();
        request.setDecision(decision);
        request.setDelistListing(delist);
        request.setAdminNotes("Reviewed");
        return request;
    }

    private ListingReport report(Long id, Long listingId, Long reporterId, ListingReport.Status status) {
        ListingReport report = new ListingReport();
        report.setId(id);
        report.setListingId(listingId);
        report.setReporterId(reporterId);
        report.setReason(ListingReport.Reason.SUSPECTED_SCAM);
        report.setStatus(status);
        return report;
    }

    private void stubReportView() {
        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 18L, "Bike", 150.0, "ACTIVE", null)));
        when(accounts.find(12L)).thenReturn(Optional.empty());
    }

    @Test
    void submitReportUsesTokenIdentityAndListingOwner() {
        asUser(12L);
        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 18L, "Bike", 150.0, "ACTIVE", null)));
        when(reports.countByReporterIdAndCreatedAtAfter(eq(12L), any(LocalDateTime.class))).thenReturn(0L);
        when(reports.existsByListingIdAndReporterIdAndStatusIn(eq(7L), eq(12L), anyList())).thenReturn(false);
        when(reports.save(any())).thenAnswer(invocation -> {
            ListingReport saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        var response = service.submitReport(submit(7L));

        assertEquals(5L, response.getId());
        assertEquals(7L, response.getListingId());
        assertEquals("Bike", response.getListingTitle());
        assertEquals(12L, response.getReporterId());
        assertEquals("alice", response.getReporterUsername());
        assertNull(response.getAdminId());
        assertNull(response.getAdminNotes());

        var captured = ArgumentCaptor.forClass(ListingReport.class);
        verify(reports).save(captured.capture());
        assertEquals(7L, captured.getValue().getListingId());
        assertEquals(12L, captured.getValue().getReporterId());
        assertNull(captured.getValue().getAdminId());
        verifyNoInteractions(accounts);
    }

    @Test
    void missingListingAndSelfReportAreRejected() {
        asUser(12L);
        when(marketplace.find(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.submitReport(submit(404L)));

        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 12L, "Bike", 150.0, "ACTIVE", null)));
        assertThrows(InformationExistException.class, () -> service.submitReport(submit(7L)));
        verify(reports, never()).save(any());
    }

    @Test
    void dailyLimitIsEnforced() {
        asUser(12L);
        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 18L, "Bike", 150.0, "ACTIVE", null)));
        when(reports.countByReporterIdAndCreatedAtAfter(eq(12L), any(LocalDateTime.class))).thenReturn(5L);

        assertThrows(RateLimitException.class, () -> service.submitReport(submit(7L)));
        verify(reports, never()).save(any());
    }

    @Test
    void anOpenReportBlocksADuplicate() {
        asUser(12L);
        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 18L, "Bike", 150.0, "ACTIVE", null)));
        when(reports.countByReporterIdAndCreatedAtAfter(eq(12L), any(LocalDateTime.class))).thenReturn(0L);
        when(reports.existsByListingIdAndReporterIdAndStatusIn(eq(7L), eq(12L), anyList())).thenReturn(true);

        assertThrows(InformationExistException.class, () -> service.submitReport(submit(7L)));
        verify(reports, never()).save(any());
    }

    @Test
    void nonAdminsCannotReachAdminFeatures() {
        asUser(12L);
        assertThrows(ResourceNotFoundException.class, () -> service.getAllReports(null, 0, 10));
        assertThrows(ResourceNotFoundException.class, () -> service.getReport(5L));
        assertThrows(ResourceNotFoundException.class, () -> service.resolveReport(5L,
                resolve(ListingReport.Status.DISMISSED, false)));
        verifyNoInteractions(reports);
    }

    @Test
    void adminPageResolvesEachListingAndReporterOnce() {
        asAdmin();
        ListingReport first = report(5L, 7L, 12L, ListingReport.Status.PENDING);
        first.setAdminId(99L);
        first.setAdminNotes("internal");
        ListingReport second = report(6L, 7L, 13L, ListingReport.Status.PENDING);
        when(reports.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 18L, "Bike", 150.0, "ACTIVE", null)));
        when(accounts.find(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "alice", "alice@example.com", "USER")));
        when(accounts.find(13L)).thenReturn(Optional.of(
                new UserSummary(13L, null, "bob@example.com", "USER")));

        var page = service.getAllReports(null, 0, 10);

        assertEquals(2, page.getContent().size());
        assertEquals("Bike", page.getContent().get(0).getListingTitle());
        assertEquals("alice", page.getContent().get(0).getReporterUsername());
        assertEquals("bob@example.com", page.getContent().get(1).getReporterUsername());
        assertEquals(99L, page.getContent().get(0).getAdminId());
        assertEquals("internal", page.getContent().get(0).getAdminNotes());
        verify(marketplace, times(1)).find(7L);
        verify(accounts, times(1)).find(12L);
        verify(accounts, times(1)).find(13L);
    }

    @Test
    void myReportsHideAdminFieldsAndUseTheTokenUsername() {
        asUser(12L);
        ListingReport mine = report(5L, 7L, 12L, ListingReport.Status.ACTION_TAKEN);
        mine.setAdminId(99L);
        mine.setAdminNotes("secret");
        when(reports.findByReporterIdOrderByCreatedAtDesc(eq(12L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(mine)));
        when(marketplace.find(7L)).thenReturn(Optional.of(
                new ListingSummary(7L, 18L, "Bike", 150.0, "ACTIVE", null)));

        var page = service.getMyReports(0, 10);

        assertEquals("alice", page.getContent().get(0).getReporterUsername());
        assertNull(page.getContent().get(0).getAdminId());
        assertNull(page.getContent().get(0).getAdminNotes());
        verifyNoInteractions(accounts);
    }

    @Test
    void resolvingWithDelistMarksTheReportAsDelistingAndPublishesTheCommand() {
        asAdmin();
        ListingReport report = report(5L, 7L, 12L, ListingReport.Status.UNDER_REVIEW);
        when(reports.findById(5L)).thenReturn(Optional.of(report));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubReportView();

        var response = service.resolveReport(5L, resolve(ListingReport.Status.ACTION_TAKEN, true));

        assertEquals(ListingReport.Status.DELISTING, response.getStatus());
        assertNull(response.getResolvedAt(), "the delist has not been confirmed yet");
        assertEquals(99L, response.getAdminId());
        assertEquals("Reviewed", response.getAdminNotes());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        ListingDelistRequested command = (ListingDelistRequested) published.getValue();
        assertEquals(5L, command.reportId());
        assertEquals(7L, command.listingId());
        assertEquals(99L, command.adminId());
        assertEquals("admin", command.adminUsername());
        assertEquals("Removed by moderation (report #5)", command.reason());
    }

    @Test
    void resolvingWithoutDelistStillResolvesImmediately() {
        asAdmin();
        ListingReport report = report(5L, 7L, 12L, ListingReport.Status.UNDER_REVIEW);
        when(reports.findById(5L)).thenReturn(Optional.of(report));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubReportView();

        var response = service.resolveReport(5L, resolve(ListingReport.Status.ACTION_TAKEN, false));

        assertEquals(ListingReport.Status.ACTION_TAKEN, response.getStatus());
        assertNotNull(response.getResolvedAt());
        verifyNoInteractions(events);
    }

    @Test
    void reportsAlreadyBeingDelistedCannotBeResolvedAgain() {
        asAdmin();
        when(reports.findById(5L))
                .thenReturn(Optional.of(report(5L, 7L, 12L, ListingReport.Status.DELISTING)));

        assertThrows(InformationExistException.class,
                () -> service.resolveReport(5L, resolve(ListingReport.Status.DISMISSED, false)));
        verify(reports, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void aDelistedResultClosesTheReport() {
        ListingReport report = report(5L, 7L, 12L, ListingReport.Status.DELISTING);
        report.setAdminNotes("Reviewed");
        when(reports.findById(5L)).thenReturn(Optional.of(report));

        service.applyDelistResult(new ListingDelistResult(5L, DelistOutcome.DELISTED, "done"));

        assertEquals(ListingReport.Status.ACTION_TAKEN, report.getStatus());
        assertNotNull(report.getResolvedAt());
        assertEquals("Reviewed Listing delisted.", report.getAdminNotes());
        verify(reports).save(report);
    }

    @Test
    void permanentRefusalsCloseTheReportWithANote() {
        ListingReport already = report(5L, 7L, 12L, ListingReport.Status.DELISTING);
        when(reports.findById(5L)).thenReturn(Optional.of(already));
        service.applyDelistResult(
                new ListingDelistResult(5L, DelistOutcome.ALREADY_DELISTED, "gone"));
        assertEquals(ListingReport.Status.ACTION_TAKEN, already.getStatus());
        assertEquals("Listing was already delisted.", already.getAdminNotes());

        ListingReport sold = report(6L, 7L, 12L, ListingReport.Status.DELISTING);
        when(reports.findById(6L)).thenReturn(Optional.of(sold));
        service.applyDelistResult(new ListingDelistResult(6L, DelistOutcome.SOLD, "sold"));
        assertEquals(ListingReport.Status.ACTION_TAKEN, sold.getStatus());
        assertEquals("Listing not delisted: the listing is sold.", sold.getAdminNotes());

        ListingReport missing = report(7L, 404L, 12L, ListingReport.Status.DELISTING);
        when(reports.findById(7L)).thenReturn(Optional.of(missing));
        service.applyDelistResult(new ListingDelistResult(7L, DelistOutcome.NOT_FOUND, "missing"));
        assertEquals(ListingReport.Status.ACTION_TAKEN, missing.getStatus());
        assertEquals("Listing not delisted: listing not found.", missing.getAdminNotes());
    }

    @Test
    void aFailedResultKeepsTheReportDelistingForTheRetrySweep() {
        ListingReport report = report(5L, 7L, 12L, ListingReport.Status.DELISTING);
        when(reports.findById(5L)).thenReturn(Optional.of(report));

        service.applyDelistResult(new ListingDelistResult(5L, DelistOutcome.FAILED, "boom"));

        assertEquals(ListingReport.Status.DELISTING, report.getStatus());
        assertNull(report.getResolvedAt());
        verify(reports, never()).save(any());
    }

    @Test
    void duplicateResultsAndMissingReportsAreIgnored() {
        ListingReport resolved = report(5L, 7L, 12L, ListingReport.Status.ACTION_TAKEN);
        when(reports.findById(5L)).thenReturn(Optional.of(resolved));
        service.applyDelistResult(new ListingDelistResult(5L, DelistOutcome.DELISTED, "again"));
        assertEquals(ListingReport.Status.ACTION_TAKEN, resolved.getStatus());

        when(reports.findById(404L)).thenReturn(Optional.empty());
        service.applyDelistResult(new ListingDelistResult(404L, DelistOutcome.DELISTED, "missing"));

        verify(reports, never()).save(any());
    }
}
