package com.example.marketplace.messaging.payment;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

/** DLT records from deserialization failures contain the original raw bytes. */
public class PaymentKafkaValueSerializer implements Serializer<Object> {
    private final JacksonJsonSerializer<Object> json = new JacksonJsonSerializer<>();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        json.configure(configs, isKey);
    }

    @Override
    public byte[] serialize(String topic, Object value) {
        return value instanceof byte[] bytes ? bytes : json.serialize(topic, value);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, Object value) {
        return value instanceof byte[] bytes ? bytes : json.serialize(topic, headers, value);
    }

    @Override
    public void close() {
        json.close();
    }
}
