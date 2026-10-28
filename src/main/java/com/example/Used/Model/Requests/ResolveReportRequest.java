package com.example.Used.Model.Requests;

import com.example.Used.Model.ListingReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveReportRequest {

    /** DISMISSED or ACTION_TAKEN — the final outcome. */
    @NotNull(message = "Decision is required")
    private ListingReport.Status decision;

    @Size(max = 1000, message = "Admin notes must be 1000 characters or less")
    private String adminNotes;

    /** Only used when decision = ACTION_TAKEN: try to delist the reported listing. */
    private boolean delistListing;
}
