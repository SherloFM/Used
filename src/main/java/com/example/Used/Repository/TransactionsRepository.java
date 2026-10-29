package com.example.Used.Repository;

import com.example.Used.Model.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TransactionsRepository extends JpaRepository<Transactions, Long> {
    List<Transactions> findByBuyerId(Long buyerId);

    List<Transactions> findBySellerId(Long sellerId);

    List<Transactions> findByListingId(Long listingId);

    /** One query for the buyer usernames of several sold listings (avoids N+1). */
    @Query("SELECT t FROM Transactions t JOIN FETCH t.buyer WHERE t.listing.id IN :listingIds")
    List<Transactions> findWithBuyerByListingIdIn(@Param("listingIds") Collection<Long> listingIds);
}
