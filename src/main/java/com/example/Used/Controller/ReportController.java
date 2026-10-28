package com.example.Used.Controller;

import com.example.Used.Model.Requests.SubmitReportRequest;
import com.example.Used.Model.Responses.ReportResponse;
import com.example.Used.Service.ReportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // Submit a report (the reporter is always taken from the JWT, never from the request body)
    @PostMapping
    public ResponseEntity<ReportResponse> submitReport(
            @Valid @RequestBody SubmitReportRequest request
    ) {
        return ResponseEntity.ok(reportService.submitReport(request));
    }

    // My own submitted reports (internal admin notes are not included)
    @GetMapping("/mine")
    public Page<ReportResponse> myReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return reportService.getMyReports(page, size);
    }
}
