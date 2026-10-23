package com.example.Used.Repository;

import com.example.Used.Model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Layer-4 defense (double click / retry): finds any ACTIVE payment for a
     * listing. Combined with the partial unique index
     * (see db/payment-indexes.sql) the DATABASE itself refuses a second
     * concurrent payment for the same listing.
     */
    Optional<Payment> findFirstByListingIdAndStatusInOrderByIdDesc(
            Long listingId,
            Collection<Payment.Status> statuses
    );

    /** Idempotency for the buyer: "do I already have an active payment for this listing?" */
    Optional<Payment> findFirstByBuyerIdAndListingIdAndStatusInOrderByIdDesc(
            Long buyerId,
            Long listingId,
            Collection<Payment.Status> statuses
    );

    List<Payment> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<Payment> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
}
