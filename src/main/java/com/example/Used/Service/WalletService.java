package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.Responses.WalletStatus;
import com.example.Used.Model.User;
import com.example.Used.Model.Wallet;
import com.example.Used.Repository.WalletRepository;
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
    private final CurrentUserService currentUserService;

    public WalletService(WalletRepository walletRepository,
                         CurrentUserService currentUserService) {
        this.walletRepository = walletRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public WalletStatus getWallet() {
        User user = currentUserService.getCurrentUser();
        walletRepository.createIfMissing(user.getId());
        return WalletStatus.from(
                walletRepository.findByUserId(user.getId())
                        .orElseThrow(() -> new InformationExistException("Wallet could not be created")));
    }

    /** Used by the payment flow right before money is moved. */
    @Transactional
    public void ensureWallet(Long userId) {
        walletRepository.createIfMissing(userId);
    }

    @Transactional
    public WalletStatus addMoney(BigDecimal amount) {
        User user = currentUserService.getCurrentUser();
        walletRepository.createIfMissing(user.getId());
        walletRepository.credit(user.getId(), amount);
        return balanceOf(user.getId());
    }

    @Transactional
    public WalletStatus removeMoney(BigDecimal amount) {
        User user = currentUserService.getCurrentUser();
        walletRepository.createIfMissing(user.getId());
        if (walletRepository.debit(user.getId(), amount) == 0) {
            throw new InformationExistException("You do not have enough money in your wallet");
        }
        return balanceOf(user.getId());
    }

    private WalletStatus balanceOf(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new InformationExistException("Wallet not found"));
        return WalletStatus.from(wallet);
    }
}
