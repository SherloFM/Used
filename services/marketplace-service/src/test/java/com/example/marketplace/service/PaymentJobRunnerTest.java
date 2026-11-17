package com.example.marketplace.service;

import com.example.marketplace.messaging.payment.PaymentRequested;
import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Payment;
import com.example.marketplace.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentJobRunnerTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentWorker worker = mock(PaymentWorker.class);
    private final PaymentJobRunner runner = new PaymentJobRunner(payments, worker);
    private final PaymentRequested request = new PaymentRequested(1, 108L, 42L);
    private Payment payment;

    @BeforeEach
    void setup() {
        var listing = new Listings();
        listing.setId(42L);
        payment = new Payment();
        payment.setId(108L);
        payment.setListing(listing);
        payment.setMethod(Payment.Method.WALLET);
        payment.setStatus(Payment.Status.PENDING);
        when(payments.findById(108L)).thenReturn(Optional.of(payment));
    }

    @Test
    void completedDuplicateDoesNotChargeOrAuditAgain() {
        payment.setStatus(Payment.Status.COMPLETED);
        runner.process(request);
        verifyNoInteractions(worker);
    }

    @Test
    void failedDuplicateDoesNotExecute() {
        payment.setStatus(Payment.Status.FAILED);
        runner.process(request);
        verifyNoInteractions(worker);
    }

    @Test
    void codCannotEnterWalletWorker() {
        payment.setMethod(Payment.Method.COD);
        assertThrows(IllegalArgumentException.class, () -> runner.process(request));
        verifyNoInteractions(worker);
    }

    @Test
    void wrongListingCannotEnterWorker() {
        assertThrows(IllegalArgumentException.class, () -> runner.process(new PaymentRequested(1, 108L, 99L)));
        verifyNoInteractions(worker);
    }

    @Test
    void businessRejectionIsPersistedAfterWorkerReturnsFailure() {
        doThrow(new PaymentWorker.PaymentFailedException("INSUFFICIENT_FUNDS")).when(worker).executePayment(108L);
        runner.process(request);
        var order = inOrder(worker);
        order.verify(worker).executePayment(108L);
        order.verify(worker).markFailed(108L, "INSUFFICIENT_FUNDS");
        verify(worker, never()).onPaymentCompleted(108L);
    }

    @Test
    void transientDatabaseFailurePropagatesForKafkaRetry() {
        doThrow(new TransientDataAccessResourceException("offline")).when(worker).executePayment(108L);
        assertThrows(TransientDataAccessResourceException.class, () -> runner.process(request));
        verify(worker, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void claimLostToCompletedPaymentIsSafe() {
        doAnswer(invocation -> {
            payment.setStatus(Payment.Status.COMPLETED);
            throw new PaymentWorker.PaymentFailedException("ALREADY_HANDLED");
        }).when(worker).executePayment(108L);
        assertDoesNotThrow(() -> runner.process(request));
        verify(worker, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void activeClaimIsRetriedRatherThanAcknowledged() {
        doThrow(new PaymentWorker.PaymentFailedException("ALREADY_HANDLED")).when(worker).executePayment(108L);
        assertThrows(TransientDataAccessResourceException.class, () -> runner.process(request));
    }

    @Test
    void postCommitAuditFailureDoesNotRetryPurchase() {
        doThrow(new IllegalStateException("audit unavailable")).when(worker).onPaymentCompleted(108L);
        assertDoesNotThrow(() -> runner.process(request));
        var order = inOrder(worker);
        order.verify(worker).executePayment(108L);
        order.verify(worker).onPaymentCompleted(108L);
    }
}
