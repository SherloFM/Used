package com.example.marketplace.service;

import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.model.Wallet;
import com.example.marketplace.model.responses.WalletStatus;
import com.example.marketplace.repository.WalletRepository;
import com.example.marketplace.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WalletServiceTest {

    private final WalletRepository wallets = mock(WalletRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final WalletService service = new WalletService(wallets, actors);

    @Test
    void administratorFundingTargetsTheRequestedUserWithoutUsingTheCurrentActor() {
        Wallet funded = new Wallet(42L);
        funded.setBalance(new BigDecimal("25.50"));
        when(wallets.credit(42L, new BigDecimal("10.50"))).thenReturn(1);
        when(wallets.findByUserId(42L)).thenReturn(Optional.of(funded));

        WalletStatus result = service.fundWallet(42L, new BigDecimal("10.50"));

        assertEquals(42L, result.userId);
        assertEquals("25.50", result.balance);
        verify(wallets).createIfMissing(42L);
        verify(wallets).credit(42L, new BigDecimal("10.50"));
        verifyNoInteractions(actors);
    }

    @Test
    void failedAtomicCreditDoesNotReportSuccess() {
        when(wallets.credit(42L, BigDecimal.TEN)).thenReturn(0);

        assertThrows(InformationExistException.class,
                () -> service.fundWallet(42L, BigDecimal.TEN));

        verify(wallets, never()).findByUserId(anyLong());
    }
}
