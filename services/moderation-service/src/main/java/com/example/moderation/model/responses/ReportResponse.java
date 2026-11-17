package com.example.moderation.model.responses;

import com.example.moderation.model.ListingReport;
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

    public static ReportResponse from(ListingReport report, boolean includeInternal,
                                      String listingTitle, String reporterUsername) {
        ReportResponse dto = new ReportResponse();
        dto.setId(report.getId());
        dto.setListingId(report.getListingId());
        dto.setListingTitle(listingTitle);
        dto.setReporterId(report.getReporterId());
        dto.setReporterUsername(reporterUsername);
        dto.setReason(report.getReason());
        dto.setDescription(report.getDescription());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());
        dto.setResolvedAt(report.getResolvedAt());
        if (includeInternal) {
            dto.setAdminId(report.getAdminId());
            dto.setAdminNotes(report.getAdminNotes());
        }
        return dto;
    }
}
