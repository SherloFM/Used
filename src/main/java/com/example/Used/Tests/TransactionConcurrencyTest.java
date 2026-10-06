package com.example.Used.Tests;

import com.example.Used.Repository.ListingRepository;
import com.example.Used.Repository.TransactionsRepository;
import com.example.Used.Service.CurrentUserService;
import com.example.Used.Service.EmailServices;
import com.example.Used.Service.TransactionServices;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransactionConcurrencyTest {
    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private EmailServices emailServices;

    @InjectMocks
    private TransactionServices transactionServices;
}
