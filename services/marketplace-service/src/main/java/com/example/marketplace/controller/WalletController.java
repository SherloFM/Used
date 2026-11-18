package com.example.marketplace.controller;

import com.example.marketplace.model.requests.MoneyRequest;
import com.example.marketplace.model.responses.WalletStatus;
import com.example.marketplace.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Wallet balance for a member and administrator-controlled wallet funding. */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public WalletStatus getWallet() {
        return walletService.getWallet();
    }

    @PostMapping("/admin/{userId}/fund")
    public WalletStatus fundWallet(@PathVariable Long userId,
                                   @Valid @RequestBody MoneyRequest request) {
        return walletService.fundWallet(userId, request.getAmount());
    }
}
