package com.example.moderation.messaging;

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
public class ListingCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(ListingCommandPublisher.class);

    /** How long to wait for the broker to confirm one publish. */
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    public ListingCommandPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Asks Marketplace to delist a listing. The report ID is reused as the message
     * ID, so retries of the same report are recognisable. The message is persistent
     * and mandatory.
     *
     * @return true only when the broker confirmed the publish AND the message was
     *         routable. The broker confirms unroutable messages too, so the
     *         returned-message check is what catches a missing queue.
     */
    public boolean requestDelist(ListingDelistRequested command) {

        CorrelationData correlation = new CorrelationData("delist-" + command.reportId());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitTopology.COMMAND_EXCHANGE,
                    RabbitTopology.DELIST_ROUTING_KEY,
                    command,
                    message -> {
                        message.getMessageProperties().setMessageId(String.valueOf(command.reportId()));
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlation);
        } catch (RuntimeException exception) {
            log.error("Could not send the delist command for report {}",
                    command.reportId(), exception);
            return false;
        }

        return awaitConfirmation(correlation, command.reportId());
    }

    private boolean awaitConfirmation(CorrelationData correlation, Long reportId) {

        try {
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                log.error("Broker rejected the delist command for report {}: {}",
                        reportId, confirm.reason());
                return false;
            }
            if (correlation.getReturned() != null) {
                log.error("Delist command for report {} was returned as unroutable: {}",
                        reportId, correlation.getReturned().getReplyText());
                return false;
            }

            log.info("Published the delist command for report {}", reportId);
            return true;

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for the delist confirm of report {}", reportId);
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            log.error("No confirm for the delist command of report {}", reportId, exception);
            return false;
        }
    }
}
