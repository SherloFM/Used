package com.example.Used.Repository;

import com.example.Used.Model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface






WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    /**
     * Layer-3 defense (deadlock prevention): locks the wallet row FOR UPDATE
     * until the surrounding transaction commits or rolls back.
     * Callers MUST lock wallets in ascending user_id order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByIdForUpdate(@Param("userId") Long userId);

    /**
     * Layer-2 defense (balance race): check and debit in ONE statement.
     * Two threads can never both pass a balance check, because the check
     * happens inside the UPDATE itself on the locked row.
     *
     * Returns the number of rows updated: 0 = insufficient funds.
     *
     * NOTE: bulk JPQL updates bypass the Hibernate persistence context and do
     * NOT fire @UpdateTimestamp. The worker must not hold stale Wallet entities
     * in its session when calling this (re-read after, or flush+clear first).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount " +
           "WHERE w.user.id = :userId AND w.balance >= :amount")
    int debit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** Atomic credit (counterpart of debit). Returns rows updated (0 = no wallet). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance + :amount " +
           "WHERE w.user.id = :userId")
    int credit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
