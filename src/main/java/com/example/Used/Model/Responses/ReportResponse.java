package com.example.Used.Model.Responses;

import com.example.Used.Model.ListingReport;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReportResponse {

    private Long id;
    private Long listingId;
    private String listingTitle;
    private Long reporterId;
    private String reporterUsername;
    private ListingReport.Reason reason;
    private String description;
    private ListingReport.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    // --- Internal fields: only populated for administrators ---
    private Long adminId;
    private String adminNotes;

    public static ReportResponse from(ListingReport report, boolean includeInternal) {
        ReportResponse dto = new ReportResponse();
        dto.setId(report.getId());
        dto.setListingId(report.getListing().getId());
        dto.setListingTitle(report.getListing().getTitle());
        dto.setReporterId(report.getReporter().getId());
        dto.setReporterUsername(report.getReporter().getUsername());
        dto.setReason(report.getReason());
        dto.setDescription(report.getDescription());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());
        dto.setResolvedAt(report.getResolvedAt());
        if (includeInternal) {
            dto.setAdminId(report.getAdmin() != null ? report.getAdmin().getId() : null);
            dto.setAdminNotes(report.getAdminNotes());
        }
        return dto;
    }
}
