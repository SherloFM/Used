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
 * Wallet operations for the logged-in user. Every balance change is one
 * atomic SQL statement (see WalletRepository), so two tabs adding or removing
 * money at the same time can never lose an update.
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
    public WalletStatus addMoney(BigDecimal amount) {
        CurrentActor actor = currentActorProvider.get();
        walletRepository.createIfMissing(actor.id());
        walletRepository.credit(actor.id(), amount);
        return balanceOf(actor.id());
    }

    @Transactional
    public WalletStatus removeMoney(BigDecimal amount) {
        CurrentActor actor = currentActorProvider.get();
        walletRepository.createIfMissing(actor.id());
        if (walletRepository.debit(actor.id(), amount) == 0) {
            throw new InformationExistException("You do not have enough money in your wallet");
        }
        return balanceOf(actor.id());
    }

    private WalletStatus balanceOf(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new InformationExistException("Wallet not found"));
        return WalletStatus.from(wallet);
    }
}
