package com.example.marketplace.messaging.payment;

import com.example.marketplace.model.Payment;
import com.example.marketplace.repository.PaymentRepository;
import com.example.marketplace.service.PaymentJobRunner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentDispatchTest {
    private final PaymentRequested request = new PaymentRequested(1, 108L, 42L);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void listenerProcessesOnCallingThreadBeforeAcknowledging() {
        var runner = mock(PaymentJobRunner.class);
        var ack = mock(Acknowledgment.class);
        Thread listenerThread = Thread.currentThread();
        doAnswer(invocation -> {
            assertSame(listenerThread, Thread.currentThread());
            verifyNoInteractions(ack);
            return null;
        }).when(runner).process(request);
        new PaymentRequestsListener(runner).receive(record("42"), ack);
        var order = inOrder(runner, ack);
        order.verify(runner).process(request);
        order.verify(ack).acknowledge();
    }

    @Test
    void listenerFailureDoesNotAcknowledge() {
        var runner = mock(PaymentJobRunner.class);
        var ack = mock(Acknowledgment.class);
        doThrow(new TransientDataAccessResourceException("database unavailable")).when(runner).process(request);
        assertThrows(TransientDataAccessResourceException.class,
                () -> new PaymentRequestsListener(runner).receive(record("42"), ack));
        verifyNoInteractions(ack);
    }

    @Test
    void mismatchedKeyNeverExecutesPayment() {
        var runner = mock(PaymentJobRunner.class);
        var ack = mock(Acknowledgment.class);
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentRequestsListener(runner).receive(record("99"), ack));
        verifyNoInteractions(runner, ack);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publisherWaitsForCommitAndUsesListingKey() {
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), any(), any())).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization();
        new PaymentRequestPublisher(kafka).publishAfterCommit(request);
        verifyNoInteractions(kafka);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafka).send("payment-requests", "42", request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackDoesNotPublish() {
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        TransactionSynchronizationManager.initSynchronization();
        new PaymentRequestPublisher(kafka).publishAfterCommit(request);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(kafka);
    }

    @Test
    @SuppressWarnings("unchecked")
    void synchronousBrokerFailureLeavesRecoveryPossible() {
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), any(), any())).thenThrow(new org.apache.kafka.common.errors.TimeoutException());
        assertDoesNotThrow(() -> new PaymentRequestPublisher(kafka).publish(request));
    }

    @Test
    void recoveryAdvancesCursorThenWrapsWithoutStarvingLaterPayments() {
        var payments = mock(PaymentRepository.class);
        var publisher = mock(PaymentRequestPublisher.class);
        when(payments.findPendingDispatchBatch(eq(Payment.Status.PENDING), eq(Payment.Method.WALLET),
                any(), eq(0L), any())).thenReturn(List.<Object[]>of(new Object[]{108L, 42L}));
        when(payments.findPendingDispatchBatch(eq(Payment.Status.PENDING), eq(Payment.Method.WALLET),
                any(), eq(108L), any())).thenReturn(List.of());
        var recovery = new PaymentDispatchRecovery(payments, publisher, 1, 120);
        recovery.recover();
        recovery.recover();
        recovery.recover();
        verify(publisher, times(2)).publish(request);
        verify(payments).findPendingDispatchBatch(eq(Payment.Status.PENDING), eq(Payment.Method.WALLET),
                any(), eq(108L), any());
    }

    @Test
    void serializerPreservesMalformedPayloadForDlt() {
        var serializer = new PaymentKafkaValueSerializer();
        byte[] malformed = "{broken-json".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(malformed, serializer.serialize("payment-requests.DLT", malformed));
        String json = new String(serializer.serialize("payment-requests", request), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"paymentId\":108"));
        serializer.close();
    }

    private ConsumerRecord<String, PaymentRequested> record(String key) {
        return new ConsumerRecord<>(PaymentKafkaConfiguration.TOPIC, 0, 0L, key, request);
    }
}
