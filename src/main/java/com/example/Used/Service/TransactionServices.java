package com.example.Used.Service;

import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.TransactionsRepository;
import org.springframework.stereotype.Service;

@Service
public class TransactionServices {
    private final TransactionsRepository transactionsRepository;
    private final ListingRepository listingRepository;
    private final CurrentUserService currentUserService;
    private final EmailServices emailServices;

    public TransactionServices(TransactionsRepository transactionsRepository,
                               ListingRepository listingRepository,
                               CurrentUserService currentUserService,
                               EmailServices emailServices) {
        this.transactionsRepository = transactionsRepository;
        this.listingRepository = listingRepository;
        this.currentUserService = currentUserService;
        this.emailServices = emailServices;
    }


}
