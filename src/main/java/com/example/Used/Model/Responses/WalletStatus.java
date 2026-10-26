package com.example.Used.Model.Responses;

import com.example.Used.Model.Wallet;

/** Current balance of the logged-in user's wallet. */
public class WalletStatus {

    public Long userId;
    public String balance;

    public static WalletStatus from(Wallet wallet) {
        WalletStatus status = new WalletStatus();
        status.userId = wallet.getUser().getId();
        status.balance = wallet.getBalance().toPlainString();
        return status;
    }
}
