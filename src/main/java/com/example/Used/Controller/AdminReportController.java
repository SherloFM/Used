package com.example.Used.Controller;

import com.example.Used.Model.ListingReport;
import com.example.Used.Model.Requests.ResolveReportRequest;
import com.example.Used.Model.Responses.ReportResponse;
import com.example.Used.Service.ReportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrator report review endpoints.
 * Class-level @PreAuthorize keeps every endpoint admin-only on the backend.
 */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // All reports with an optional status filter
    @GetMapping
    public Page<ReportResponse> allReports(
            @RequestParam(required = false) ListingReport.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return reportService.getAllReports(status, page, size);
    }

    // Previous reports for the same listing (history view)
    @GetMapping("/listing/{listingId}")
    public ResponseEntity<List<ReportResponse>> reportsForListing(
            @PathVariable Long listingId
    ) {
        return ResponseEntity.ok(reportService.getReportsForListing(listingId));
    }

    // One report with internal notes
    @GetMapping("/{id}")
    public ReportResponse report(@PathVariable Long id) {
        return reportService.getReport(id);
    }

    // Mark a pending report as UNDER_REVIEW
    @PutMapping("/{id}/status")
    public ReportResponse markUnderReview(@PathVariable Long id) {
        return reportService.markUnderReview(id);
    }

    // Resolve: {decision: DISMISSED|ACTION_TAKEN, adminNotes?, delistListing?}
    @PutMapping("/{id}/resolve")
    public ReportResponse resolveReport(
            @PathVariable Long id,
            @Valid @RequestBody ResolveReportRequest request
    ) {
        return reportService.resolveReport(id, request);
    }
}
