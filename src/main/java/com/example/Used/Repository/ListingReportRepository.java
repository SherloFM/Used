package com.example.Used.Repository;

import com.example.Used.Model.ListingReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    // Administrator views
    Page<ListingReport> findByStatus(ListingReport.Status status, Pageable pageable);
    Page<ListingReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ListingReport> findByListingIdOrderByCreatedAtDesc(Long listingId);

    // User views
    Page<ListingReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    // Duplicate policy: pending or under-review reports block a new one
    boolean existsByListingIdAndReporterIdAndStatusIn(
            Long listingId, Long reporterId, List<ListingReport.Status> statuses);

    // Light rate limit: reports this user submitted since a point in time
    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime after);
}
