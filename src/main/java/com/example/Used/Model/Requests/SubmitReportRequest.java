package com.example.Used.Model.Requests;

import com.example.Used.Model.ListingReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitReportRequest {

    @NotNull(message = "Listing to report is required")
    private Long listingId;

    @NotNull(message = "Report reason is required")
    private ListingReport.Reason reason;

    @Size(max = 1000, message = "Description must be 1000 characters or less")
    private String description;
}
