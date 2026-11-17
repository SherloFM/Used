package com.example.moderation.messaging;

import com.example.moderation.client.AccountsClient;
import com.example.moderation.client.UserSummary;
import com.example.moderation.model.ListingReport;
import com.example.moderation.repository.ListingReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DelistRetrySchedulerTest {

    private ListingReportRepository reports;
    private AccountsClient accounts;
    private ListingCommandPublisher publisher;
    private DelistRetryScheduler scheduler;

    @BeforeEach
    void setup() {
        reports = mock(ListingReportRepository.class);
        accounts = mock(AccountsClient.class);
        publisher = mock(ListingCommandPublisher.class);
        scheduler = new DelistRetryScheduler(reports, accounts, publisher, 60_000L, 100);
    }

    private ListingReport delistingReport() {
        ListingReport report = new ListingReport();
        report.setId(5L);
        report.setListingId(7L);
        report.setReporterId(12L);
        report.setReason(ListingReport.Reason.SUSPECTED_SCAM);
        report.setStatus(ListingReport.Status.DELISTING);
        report.setAdminId(99L);
        return report;
    }

    @Test
    void republishesStaleDelistingReports() {
        when(reports.findByStatusAndUpdatedAtBefore(
                eq(ListingReport.Status.DELISTING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(delistingReport()));
        when(accounts.find(99L)).thenReturn(Optional.of(
                new UserSummary(99L, "admin", "admin@example.com", "ADMIN")));
        when(publisher.requestDelist(any())).thenReturn(true);

        scheduler.republishStaleDelists();

        ArgumentCaptor<ListingDelistRequested> captured =
                ArgumentCaptor.forClass(ListingDelistRequested.class);
        verify(publisher).requestDelist(captured.capture());
        assertEquals(5L, captured.getValue().reportId());
        assertEquals(7L, captured.getValue().listingId());
        assertEquals(99L, captured.getValue().adminId());
        assertEquals("admin", captured.getValue().adminUsername());
        assertEquals("Removed by moderation (report #5)", captured.getValue().reason());
    }

    @Test
    void aMissingOrUnreachableAccountFallsBackToAnAdminName() {
        when(reports.findByStatusAndUpdatedAtBefore(
                eq(ListingReport.Status.DELISTING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(delistingReport()));
        when(accounts.find(99L)).thenReturn(Optional.empty());
        when(publisher.requestDelist(any())).thenReturn(true);

        scheduler.republishStaleDelists();

        ArgumentCaptor<ListingDelistRequested> captured =
                ArgumentCaptor.forClass(ListingDelistRequested.class);
        verify(publisher).requestDelist(captured.capture());
        assertEquals("admin", captured.getValue().adminUsername());
    }

    @Test
    void anAccountLookupFailureDoesNotStopTheRetry() {
        when(reports.findByStatusAndUpdatedAtBefore(
                eq(ListingReport.Status.DELISTING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(delistingReport()));
        when(accounts.find(99L)).thenThrow(new IllegalStateException("Accounts is down"));
        when(publisher.requestDelist(any())).thenReturn(true);

        scheduler.republishStaleDelists();

        verify(publisher).requestDelist(any());
    }

    @Test
    void nothingToDoMeansNoPublishing() {
        when(reports.findByStatusAndUpdatedAtBefore(
                eq(ListingReport.Status.DELISTING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.republishStaleDelists();

        verifyNoInteractions(publisher);
    }

    @Test
    void aDatabaseFailureIsSwallowedUntilTheNextSweep() {
        when(reports.findByStatusAndUpdatedAtBefore(
                eq(ListingReport.Status.DELISTING), any(LocalDateTime.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("database is down"));

        scheduler.republishStaleDelists();

        verify(publisher, never()).requestDelist(any());
    }
}
