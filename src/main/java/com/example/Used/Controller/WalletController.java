package com.example.Used.Controller;

import com.example.Used.Model.Requests.MoneyRequest;
import com.example.Used.Model.Responses.WalletStatus;
import com.example.Used.Service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Balance, deposit and withdrawal for the logged-in user's wallet. */
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

    @PostMapping("/add")
    public WalletStatus addMoney(@Valid @RequestBody MoneyRequest request) {
        return walletService.addMoney(request.getAmount());
    }

    @PostMapping("/remove")
    public WalletStatus removeMoney(@Valid @RequestBody MoneyRequest request) {
        return walletService.removeMoney(request.getAmount());
    }
}
