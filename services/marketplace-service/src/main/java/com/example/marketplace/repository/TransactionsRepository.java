package com.example.marketplace.repository;

import com.example.marketplace.model.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TransactionsRepository extends JpaRepository<Transactions, Long> {
    List<Transactions> findByBuyerId(Long buyerId);

    List<Transactions> findBySellerId(Long sellerId);

    List<Transactions> findByListingId(Long listingId);

    /** All sales for a set of listings; buyer display data comes from Accounts. */
    List<Transactions> findByListingIdIn(Collection<Long> listingIds);
}
