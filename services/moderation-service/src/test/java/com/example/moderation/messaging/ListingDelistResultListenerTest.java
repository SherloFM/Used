package com.example.moderation.messaging;

import com.example.moderation.service.ReportService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ListingDelistResultListenerTest {

    @Test
    void handsEveryResultToTheIdempotentReportService() {
        ReportService reports = mock(ReportService.class);
        ListingDelistResultListener listener = new ListingDelistResultListener(reports);
        ListingDelistResult result =
                new ListingDelistResult(5L, DelistOutcome.DELISTED, "Listing delisted.");

        listener.onDelistResult(result);

        verify(reports).applyDelistResult(result);
    }
}
