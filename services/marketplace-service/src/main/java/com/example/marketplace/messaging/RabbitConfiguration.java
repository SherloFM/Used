package com.example.marketplace.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfiguration.class);

    /**
     * JSON on the wire, so messages stay readable, reviewable, and independent
     * of Java serialization.
     *
     * <p>The type id is a logical contract name, not a Java class name. Each
     * service owns its own copy of every contract, so a message must never carry
     * a class name from another service: it would be refused as an untrusted
     * type, or fail because the class does not exist in that process. Here the
     * producer writes "listing.event" and every consumer maps that id to its own
     * record.</p>
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        DefaultJacksonJavaTypeMapper mapper = new DefaultJacksonJavaTypeMapper();
        mapper.setIdClassMapping(Map.of(
                RabbitTopology.TYPE_LISTING_EVENT, ListingEvent.class,
                RabbitTopology.TYPE_DELIST_COMMAND, ListingDelistRequested.class,
                RabbitTopology.TYPE_DELIST_RESULT, ListingDelistResult.class));
        converter.setJavaTypeMapper(mapper);
        return converter;
    }

    /**
     * Mandatory publishing turns an unroutable message into a returned message
     * instead of a silent drop, and confirms surface broker-side rejections.
     * Note that an unroutable message is still confirmed; the return is the
     * failure signal, so retry decisions must look at both.
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);
            template.setReturnsCallback(returned -> log.error(
                    "Unroutable message: exchange={} routingKey={} replyCode={} replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText()));
            template.setConfirmCallback((correlation, acknowledged, cause) -> {
                if (!acknowledged) {
                    log.error("Broker rejected message: id={} cause={}",
                            correlation == null ? null : correlation.getId(), cause);
                }
            });
        };
    }
}
