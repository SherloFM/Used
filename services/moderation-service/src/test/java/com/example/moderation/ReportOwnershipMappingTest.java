package com.example.moderation;

import com.example.moderation.model.ListingReport;
import com.example.moderation.model.responses.ReportResponse;
import com.example.moderation.repository.ListingReportRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false"
})
class ReportOwnershipMappingTest {
    @Autowired private ListingReportRepository reports;
    @Autowired private EntityManager entityManager;

    @Test
    void reportStoresScalarListingReporterAndOptionalAdminIds() {
        ListingReport saved = saveReport(7L, 12L, ListingReport.Status.PENDING, null);
        entityManager.flush();
        entityManager.clear();
        ListingReport loaded = reports.findById(saved.getId()).orElseThrow();
        assertEquals(7L, loaded.getListingId());
        assertEquals(12L, loaded.getReporterId());
        assertNull(loaded.getAdminId());

        loaded.setAdminId(99L);
        reports.save(loaded);
        entityManager.flush();
        entityManager.clear();
        assertEquals(99L, reports.findById(saved.getId()).orElseThrow().getAdminId());
    }

    @Test
    void listingHistoryReturnsOnlyThatListingsReportsNewestFirst() {
        ListingReport older = saveReport(7L, 12L, ListingReport.Status.PENDING, null);
        ListingReport newer = saveReport(7L, 13L, ListingReport.Status.DISMISSED, null);
        saveReport(8L, 12L, ListingReport.Status.PENDING, null);
        entityManager.flush();
        setCreatedAt(older.getId(), LocalDateTime.of(2026, 1, 1, 10, 0));
        setCreatedAt(newer.getId(), LocalDateTime.of(2026, 1, 2, 10, 0));
        entityManager.clear();

        List<ListingReport> result = reports.findByListingIdOrderByCreatedAtDesc(7L);
        assertEquals(2, result.size());
        assertEquals(newer.getId(), result.get(0).getId());
        assertEquals(older.getId(), result.get(1).getId());
    }

    @Test
    void openReportsBlockDuplicatesButDismissedReportsDoNot() {
        saveReport(7L, 12L, ListingReport.Status.DISMISSED, null);
        entityManager.flush();
        assertFalse(reports.existsByListingIdAndReporterIdAndStatusIn(7L, 12L,
                List.of(ListingReport.Status.PENDING, ListingReport.Status.UNDER_REVIEW)));

        saveReport(7L, 12L, ListingReport.Status.UNDER_REVIEW, 99L);
        entityManager.flush();
        assertTrue(reports.existsByListingIdAndReporterIdAndStatusIn(7L, 12L,
                List.of(ListingReport.Status.PENDING, ListingReport.Status.UNDER_REVIEW)));
    }

    @Test
    void dailyRateLimitCountsOnlyRecentReportsFromThatUser() {
        saveReport(7L, 12L, ListingReport.Status.PENDING, null);
        saveReport(8L, 12L, ListingReport.Status.DISMISSED, null);
        saveReport(9L, 13L, ListingReport.Status.PENDING, null);
        entityManager.flush();
        setCreatedAt(reports.findAll().stream()
                .filter(report -> report.getReporterId().equals(13L))
                .findFirst().orElseThrow().getId(), LocalDateTime.of(2025, 1, 1, 10, 0));
        entityManager.clear();

        long recent = reports.countByReporterIdAndCreatedAtAfter(12L, LocalDateTime.now().minusDays(1));
        assertEquals(2, recent);
        assertEquals(0, reports.countByReporterIdAndCreatedAtAfter(13L, LocalDateTime.now().minusDays(1)));
    }

    @Test
    void myReportsAreFilteredByReporterIdAndNewestFirst() {
        ListingReport mine = saveReport(7L, 12L, ListingReport.Status.PENDING, null);
        saveReport(8L, 13L, ListingReport.Status.PENDING, null);
        entityManager.flush();
        setCreatedAt(mine.getId(), LocalDateTime.of(2026, 1, 1, 10, 0));
        entityManager.clear();

        var page = reports.findByReporterIdOrderByCreatedAtDesc(12L,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals(mine.getId(), page.getContent().get(0).getId());
    }

    @Test
    void adminFieldsAreOnlyIncludedInAdminResponses() {
        ListingReport report = saveReport(7L, 12L, ListingReport.Status.ACTION_TAKEN, 99L);
        report.setAdminNotes("Removed");
        entityManager.flush();
        entityManager.clear();
        ListingReport loaded = reports.findById(report.getId()).orElseThrow();

        ReportResponse adminView = ReportResponse.from(loaded, true, "Bike", "alice");
        assertEquals(7L, adminView.getListingId());
        assertEquals("Bike", adminView.getListingTitle());
        assertEquals(12L, adminView.getReporterId());
        assertEquals("alice", adminView.getReporterUsername());
        assertEquals(99L, adminView.getAdminId());
        assertEquals("Removed", adminView.getAdminNotes());

        ReportResponse userView = ReportResponse.from(loaded, false, "Bike", "alice");
        assertNull(userView.getAdminId());
        assertNull(userView.getAdminNotes());
    }

    private ListingReport saveReport(Long listingId, Long reporterId, ListingReport.Status status, Long adminId) {
        ListingReport report = new ListingReport();
        report.setListingId(listingId);
        report.setReporterId(reporterId);
        report.setReason(ListingReport.Reason.SUSPECTED_SCAM);
        report.setStatus(status);
        report.setAdminId(adminId);
        return reports.save(report);
    }

    private void setCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("update listing_reports set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
    }
}
