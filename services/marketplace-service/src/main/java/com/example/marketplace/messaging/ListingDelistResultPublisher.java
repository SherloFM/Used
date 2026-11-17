package com.example.marketplace.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ListingDelistResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(ListingDelistResultPublisher.class);

    /** How long to wait for the broker to confirm one publish. */
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    public ListingDelistResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @return true only when the broker confirmed the publish AND the message was
     *         routable. A missing Moderation queue is detected through the return,
     *         not through the confirm.
     */
    public boolean publish(ListingDelistResult result) {

        CorrelationData correlation = new CorrelationData("delist-result-" + result.reportId());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitTopology.EVENT_EXCHANGE,
                    RabbitTopology.RESULT_ROUTING_KEY,
                    result,
                    message -> {
                        message.getMessageProperties().setMessageId(String.valueOf(result.reportId()));
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlation);
        } catch (RuntimeException exception) {
            log.error("Could not send the delist result for report {}",
                    result.reportId(), exception);
            return false;
        }

        return awaitConfirmation(correlation, result.reportId());
    }

    private boolean awaitConfirmation(CorrelationData correlation, Long reportId) {

        try {
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                log.error("Broker rejected the delist result for report {}: {}",
                        reportId, confirm.reason());
                return false;
            }
            if (correlation.getReturned() != null) {
                log.error("Delist result for report {} was returned as unroutable: {}",
                        reportId, correlation.getReturned().getReplyText());
                return false;
            }

            log.info("Published the delist result for report {}", reportId);
            return true;

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for the delist result confirm of report {}", reportId);
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            log.error("No confirm for the delist result of report {}", reportId, exception);
            return false;
        }
    }
}
