package com.example.marketplace.messaging.payment;

import com.example.marketplace.service.PaymentJobRunner;
import com.example.marketplace.service.PaymentWorker;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real broker/production listener configuration; payment business work is mocked. */
@EnabledIfEnvironmentVariable(named = "KAFKA_TEST_BOOTSTRAP", matches = ".+")
class PaymentKafkaBrokerTest {
    @Test
    void retriesDltRawBytesExecutorAndCommittedOffsets() throws Exception {
        String bootstrap = System.getenv("KAFKA_TEST_BOOTSTRAP");
        String topic = "payment-probe-" + UUID.randomUUID();
        String group = topic + "-workers";
        var partition = new TopicPartition(topic, 0);
        var admin = AdminClient.create(Map.of("bootstrap.servers", bootstrap));
        admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1),
                new NewTopic(topic + ".DLT", 3, (short) 1))).all().get(30, TimeUnit.SECONDS);

        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put("bootstrap.servers", bootstrap);
        producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProperties.put("value.serializer", PaymentKafkaValueSerializer.class);
        producerProperties.put("spring.json.add.type.headers", false);
        producerProperties.put("acks", "all");
        var producers = new DefaultKafkaProducerFactory<Object, Object>(producerProperties);
        var kafka = new KafkaTemplate<Object, Object>(producers);

        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put("bootstrap.servers", bootstrap);
        consumerProperties.put("enable.auto.commit", false);
        consumerProperties.put("auto.offset.reset", "earliest");
        consumerProperties.put("max.poll.records", 1);
        consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProperties.put("value.deserializer", "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        consumerProperties.put("spring.deserializer.value.delegate.class", "org.springframework.kafka.support.serializer.JacksonJsonDeserializer");
        consumerProperties.put("spring.json.value.default.type", PaymentRequested.class.getName());
        consumerProperties.put("spring.json.use.type.headers", false);

        AtomicInteger threadIds = new AtomicInteger();
        var executor = new ThreadPoolExecutor(3, 3, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> new Thread(runnable, "payment-worker-broker-test-" + threadIds.incrementAndGet()));
        var worker = mock(PaymentWorker.class);
        var runner = mock(PaymentJobRunner.class);
        var listener = new PaymentRequestsListener(runner);
        AtomicInteger transientAttempts = new AtomicInteger();
        AtomicInteger exhaustedAttempts = new AtomicInteger();
        CountDownLatch inLastJob = new CountDownLatch(1);
        CountDownLatch allowLastJob = new CountDownLatch(1);
        CountDownLatch otherPartitions = new CountDownLatch(2);
        Set<String> processingThreads = ConcurrentHashMap.newKeySet();
        doAnswer(invocation -> {
            assertTrue(Thread.currentThread().getName().startsWith("payment-worker-broker-test"));
            processingThreads.add(Thread.currentThread().getName());
            PaymentRequested request = invocation.getArgument(0);
            if (request.paymentId() == 5L || request.paymentId() == 6L) {
                otherPartitions.countDown();
            }
            if (request.paymentId() == 1L && transientAttempts.incrementAndGet() < 3) {
                throw new TransientDataAccessResourceException("temporary");
            }
            if (request.paymentId() == 2L) {
                throw new IllegalArgumentException("invalid business data");
            }
            if (request.paymentId() == 3L) {
                exhaustedAttempts.incrementAndGet();
                throw new TransientDataAccessResourceException("persistent outage");
            }
            if (request.paymentId() == 4L) {
                inLastJob.countDown();
                assertTrue(allowLastJob.await(30, TimeUnit.SECONDS));
            }
            return null;
        }).when(runner).process(any());

        var configuration = new PaymentKafkaConfiguration();
        var factory = configuration.paymentKafkaListenerContainerFactory(
                new DefaultKafkaConsumerFactory<>(consumerProperties),
                configuration.paymentKafkaErrorHandler(kafka, worker), executor, 3);
        var container = factory.createContainer(topic);
        container.getContainerProperties().setGroupId(group);
        container.setupMessageListener((AcknowledgingMessageListener<Object, Object>) (record, ack) ->
                listener.receive(new ConsumerRecord<>(record.topic(), record.partition(), record.offset(),
                        (String) record.key(), (PaymentRequested) record.value()), ack));

        try {
            container.start();
            kafka.send(topic, 1, "43", new PaymentRequested(1, 5L, 43L)).get(15, TimeUnit.SECONDS);
            kafka.send(topic, 2, "44", new PaymentRequested(1, 6L, 44L)).get(15, TimeUnit.SECONDS);
            for (long id : new long[]{1, 2, 3}) {
                kafka.send(topic, 0, "42", new PaymentRequested(1, id, 42L)).get(15, TimeUnit.SECONDS);
            }
            byte[] malformed = "{broken-json".getBytes(StandardCharsets.UTF_8);
            kafka.send(topic, 0, "42", malformed).get(15, TimeUnit.SECONDS);
            kafka.send(topic, 0, "42", new PaymentRequested(1, 4L, 42L)).get(15, TimeUnit.SECONDS);
            assertTrue(inLastJob.await(100, TimeUnit.SECONDS), "consumer must advance past DLT records");
            assertTrue(otherPartitions.await(15, TimeUnit.SECONDS));
            assertEquals(3, processingThreads.size(), "three partitions distribute work across three consumers");
            assertEquals(4L, committed(admin, group, partition), "in-flight work must not be acknowledged");
            allowLastJob.countDown();
            await(() -> committed(admin, group, partition) == 5L);
            assertEquals(3, transientAttempts.get());
            assertEquals(4, exhaustedAttempts.get(), "initial attempt plus three retries");
            verify(runner, times(1)).process(new PaymentRequested(1, 2L, 42L));
            verify(worker).markDispatchFailed(2L, 42L);
            verify(worker).markDispatchFailed(3L, 42L);

            var dltProperties = new HashMap<String, Object>();
            dltProperties.put("bootstrap.servers", bootstrap);
            dltProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            dltProperties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            dltProperties.put("enable.auto.commit", false);
            try (var dlt = new KafkaConsumer<String, byte[]>(dltProperties)) {
                var dltPartition = new TopicPartition(topic + ".DLT", 0);
                dlt.assign(List.of(dltPartition));
                dlt.seekToBeginning(List.of(dltPartition));
                List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (records.size() < 3 && System.nanoTime() < deadline) {
                    dlt.poll(Duration.ofMillis(500)).forEach(records::add);
                }
                assertEquals(3, records.size());
                assertArrayEquals(malformed, records.get(2).value());
                assertTrue(records.stream().allMatch(record -> "42".equals(record.key())));
                assertNotNull(records.get(0).headers().lastHeader("kafka_dlt-exception-fqcn"));
            }
        } finally {
            allowLastJob.countDown();
            container.stop();
            executor.shutdownNow();
            producers.destroy();
            admin.deleteTopics(List.of(topic, topic + ".DLT")).all().get(30, TimeUnit.SECONDS);
            admin.close();
        }
    }

    private long committed(AdminClient admin, String group, TopicPartition partition) {
        try {
            var offset = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS).get(partition);
            return offset == null ? -1 : offset.offset();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean());
    }
}
