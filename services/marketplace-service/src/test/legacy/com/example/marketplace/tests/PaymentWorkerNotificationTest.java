package com.example.marketplace.tests;

import com.example.accounts.repository.UserRepository;
import com.example.marketplace.repository.ListingRepository;
import com.example.marketplace.repository.PaymentRepository;
import com.example.marketplace.repository.TransactionsRepository;
import com.example.marketplace.repository.WalletRepository;

import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Payment;
import com.example.marketplace.model.Transactions;
import com.example.accounts.model.User;
import com.example.audit.service.AuditLogService;
import com.example.notification.service.EmailServices;
import com.example.favorites.service.FavoriteService;
import com.example.notification.service.NotificationService;
import com.example.marketplace.service.PaymentWorker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentWorkerNotificationTest {

    @Test
    void walletPurchaseNotifiesSavedListingUsersInsidePaymentTransaction() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        FavoriteService favorites = mock(FavoriteService.class);
        NotificationService notifications = mock(NotificationService.class);

        User buyer = user(11L);
        User seller = user(12L);
        Listings listing = new Listings();
        listing.setId(70L);

        Payment payment = new Payment();
        payment.setId(80L);
        payment.setListing(listing);
        payment.setBuyer(buyer);
        payment.setSeller(seller);
        payment.setAmount(BigDecimal.TEN);
        payment.setStatus(Payment.Status.PENDING);
        when(payments.findById(80L)).thenReturn(Optional.of(payment));
        when(payments.updateStatusIfCurrent(80L, Payment.Status.PENDING, Payment.Status.PROCESSING))
                .thenReturn(1);
        when(listings.updateStatusIfCurrent(70L, Listings.Status.ACTIVE, Listings.Status.SOLD))
                .thenReturn(1);
        when(wallets.debit(11L, BigDecimal.TEN)).thenReturn(1);
        when(wallets.credit(12L, BigDecimal.TEN)).thenReturn(1);
        when(payments.markCompletedIfCurrent(80L, Payment.Status.PROCESSING, Payment.Status.COMPLETED))
                .thenReturn(1);

        PaymentWorker worker = new PaymentWorker(
                payments,
                listings,
                wallets,
                mock(TransactionsRepository.class),
                mock(UserRepository.class),
                mock(AuditLogService.class),
                mock(EmailServices.class),
                favorites,
                notifications);

        worker.executePayment(80L);

        verify(favorites).handleListingRemoved(70L, "sold");
    }

    @Test
    void codPurchaseNotifiesSavedListingUsersAfterListingClaim() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        TransactionsRepository transactions = mock(TransactionsRepository.class);
        FavoriteService favorites = mock(FavoriteService.class);
        NotificationService notifications = mock(NotificationService.class);

        User buyer = user(11L);
        User seller = user(12L);
        Listings listing = new Listings();
        listing.setId(70L);
        listing.setTitle("Camera");

        Payment payment = new Payment();
        payment.setId(80L);
        payment.setListing(listing);
        payment.setBuyer(buyer);
        payment.setSeller(seller);
        payment.setAmount(BigDecimal.TEN);
        payment.setMethod(Payment.Method.COD);
        payment.setStatus(Payment.Status.PENDING);

        when(payments.findById(80L)).thenReturn(Optional.of(payment));
        when(payments.updateStatusIfCurrent(80L, Payment.Status.PENDING, Payment.Status.PROCESSING))
                .thenReturn(1);
        when(listings.updateStatusIfCurrent(70L, Listings.Status.ACTIVE, Listings.Status.SOLD))
                .thenReturn(1);
        when(payments.markCompletedIfCurrent(80L, Payment.Status.PROCESSING, Payment.Status.COMPLETED))
                .thenReturn(1);

        PaymentWorker worker = worker(payments, listings, transactions, favorites, notifications);
        worker.acceptCodPayment(80L, seller.getId());

        verify(favorites).handleListingRemoved(70L, "sold");
        verify(transactions).save(any(Transactions.class));
        verify(notifications).sendToUser(eq(12L), eq("LISTING_SOLD"), anyMap());
    }

    @Test
    void failedListingClaimDoesNotNotifyFavoritors() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        FavoriteService favorites = mock(FavoriteService.class);
        NotificationService notifications = mock(NotificationService.class);

        User buyer = user(11L);
        User seller = user(12L);
        Listings listing = new Listings();
        listing.setId(70L);

        Payment payment = new Payment();
        payment.setId(80L);
        payment.setListing(listing);
        payment.setBuyer(buyer);
        payment.setSeller(seller);
        payment.setAmount(BigDecimal.TEN);
        payment.setMethod(Payment.Method.COD);
        payment.setStatus(Payment.Status.PENDING);
        when(payments.findById(80L)).thenReturn(Optional.of(payment));
        when(payments.updateStatusIfCurrent(80L, Payment.Status.PENDING, Payment.Status.PROCESSING))
                .thenReturn(1);
        when(listings.updateStatusIfCurrent(70L, Listings.Status.ACTIVE, Listings.Status.SOLD))
                .thenReturn(0);

        PaymentWorker worker = worker(
                payments, listings, mock(TransactionsRepository.class), favorites, notifications);

        org.junit.jupiter.api.Assertions.assertThrows(
                PaymentWorker.PaymentFailedException.class,
                () -> worker.acceptCodPayment(80L, seller.getId()));
        verify(favorites, never()).handleListingRemoved(anyLong(), anyString());
    }

    private static PaymentWorker worker(
            PaymentRepository payments,
            ListingRepository listings,
            TransactionsRepository transactions,
            FavoriteService favorites,
            NotificationService notifications
    ) {
        return new PaymentWorker(
                payments,
                listings,
                mock(WalletRepository.class),
                transactions,
                mock(UserRepository.class),
                mock(AuditLogService.class),
                mock(EmailServices.class),
                favorites,
                notifications);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
