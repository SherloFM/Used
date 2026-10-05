package com.example.Used.Repository;

import com.example.Used.Model.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionsRepository extends JpaRepository<Transactions, Long> {
    List<Transactions> findByBuyerId(Long buyerId);

    List<Transactions> findBySellerId(Long sellerId);

    List<Transactions> findByListingId(Long listingId);
}
