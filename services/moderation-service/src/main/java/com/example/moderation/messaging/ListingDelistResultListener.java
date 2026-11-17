package com.example.moderation.messaging;

import com.example.moderation.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives what Marketplace decided and closes the report. Duplicate results
 * are harmless because {@link ReportService#applyDelistResult} is idempotent.
 */
@Component
public class ListingDelistResultListener {

    private static final Logger log = LoggerFactory.getLogger(ListingDelistResultListener.class);

    private final ReportService reportService;

    public ListingDelistResultListener(ReportService reportService) {
        this.reportService = reportService;
    }

    @RabbitListener(queues = RabbitTopology.RESULT_QUEUE)
    public void onDelistResult(ListingDelistResult result) {
        log.info("Delist result for report {}: {}", result.reportId(), result.outcome());
        reportService.applyDelistResult(result);
    }
}
