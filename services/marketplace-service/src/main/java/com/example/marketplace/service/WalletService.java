package com.example.marketplace.service;

import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.model.Wallet;
import com.example.marketplace.model.responses.WalletStatus;
import com.example.marketplace.repository.WalletRepository;
import com.example.marketplace.security.CurrentActor;
import com.example.marketplace.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Wallet operations. Every balance change is one atomic SQL statement (see
 * WalletRepository), so concurrent funding and payment cannot lose an update.
 */
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final CurrentActorProvider currentActorProvider;

    public WalletService(WalletRepository walletRepository,
                         CurrentActorProvider currentActorProvider) {
        this.walletRepository = walletRepository;
        this.currentActorProvider = currentActorProvider;
    }

    @Transactional
    public WalletStatus getWallet() {
        CurrentActor actor = currentActorProvider.get();
        walletRepository.createIfMissing(actor.id());
        return WalletStatus.from(
                walletRepository.findByUserId(actor.id())
                        .orElseThrow(() -> new InformationExistException("Wallet could not be created")));
    }

    /** Used by the payment flow right before money is moved. */
    @Transactional
    public void ensureWallet(Long userId) {
        walletRepository.createIfMissing(userId);
    }

    @Transactional
    public WalletStatus fundWallet(Long userId, BigDecimal amount) {
        walletRepository.createIfMissing(userId);
        if (walletRepository.credit(userId, amount) != 1) {
            throw new InformationExistException("Wallet could not be funded");
        }
        return balanceOf(userId);
    }

    private WalletStatus balanceOf(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new InformationExistException("Wallet not found"));
        return WalletStatus.from(wallet);
    }
}
