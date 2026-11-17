package com.example.notification.messaging;

import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfiguration {

    /**
     * The type id on the wire is a logical contract name, and this service maps
     * it to its own copy of the record. No producer class name is ever trusted
     * or loaded here.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        DefaultJacksonJavaTypeMapper mapper = new DefaultJacksonJavaTypeMapper();
        mapper.setIdClassMapping(Map.of(
                RabbitTopology.TYPE_LISTING_EVENT, ListingEvent.class));
        converter.setJavaTypeMapper(mapper);
        return converter;
    }
}
