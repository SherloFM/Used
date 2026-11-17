package com.example.marketplace.model.responses;

import com.example.marketplace.model.Wallet;

/** Current balance of the logged-in user's wallet. */
public class WalletStatus {

    public Long userId;
    public String balance;

    public static WalletStatus from(Wallet wallet) {
        WalletStatus status = new WalletStatus();
        status.userId = wallet.getUserId();
        status.balance = wallet.getBalance().toPlainString();
        return status;
    }
}
