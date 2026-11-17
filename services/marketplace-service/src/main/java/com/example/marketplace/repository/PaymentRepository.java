package com.example.marketplace.repository;

import com.example.marketplace.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("""
            select p.id, p.listing.id from Payment p
            where p.status = :status and (p.method = :method or p.method is null)
              and p.createdAt < :before and p.id > :afterId
            order by p.id
            """)
    List<Object[]> findPendingDispatchBatch(@Param("status") Payment.Status status,
            @Param("method") Payment.Method method, @Param("before") LocalDateTime before,
            @Param("afterId") Long afterId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Payment p set p.status = :failed, p.failureReason = 'KAFKA_DLT',
                p.processedAt = CURRENT_TIMESTAMP
            where p.id = :id and p.listing.id = :listingId and p.status = :pending
              and (p.method = :wallet or p.method is null)
            """)
    int failPendingDispatch(@Param("id") Long id, @Param("listingId") Long listingId,
            @Param("pending") Payment.Status pending, @Param("failed") Payment.Status failed,
            @Param("wallet") Payment.Method wallet);

    List<Payment> findByListingIdOrderByIdAsc(Long listingId);

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

    List<Payment> findBySellerIdAndMethodAndStatusOrderByCreatedAtDesc(
            Long sellerId, Payment.Method method, Payment.Status status);

    long countByListingIdAndMethodAndStatusAndIdLessThanEqual(
            Long listingId, Payment.Method method, Payment.Status status, Long id);

    List<Payment> findByMethodAndStatusAndExpiresAtBefore(
            Payment.Method method, Payment.Status status, LocalDateTime before);

    /**
     * Atomic state claim. Two Accept clicks (or two tabs) can never both win:
     * exactly one UPDATE matches the expected status.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :to WHERE p.id = :id AND p.status = :from")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("from") Payment.Status from,
                              @Param("to") Payment.Status to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :completed, p.processedAt = CURRENT_TIMESTAMP " +
           "WHERE p.id = :id AND p.status = :from")
    int markCompletedIfCurrent(@Param("id") Long id,
                               @Param("from") Payment.Status from,
                               @Param("completed") Payment.Status completed);

    /**
     * Fails the payment only while it is still active. Once COMPLETED (or
     * already FAILED) this updates 0 rows, so a late/duplicate failure can
     * never overwrite a successful payment.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :failed, p.failureReason = :reason, " +
           "p.processedAt = CURRENT_TIMESTAMP " +
           "WHERE p.id = :id AND p.status IN :active")
    int failIfActive(@Param("id") Long id,
                     @Param("reason") String reason,
                     @Param("failed") Payment.Status failed,
                     @Param("active") Collection<Payment.Status> active);
}
