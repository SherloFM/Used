package com.example.marketplace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Buyer/Seller balance used by the payment process.
 *
 * Concurrency notes (see PaymentProcess design):
 *  - Balance mutations are NEVER done as read-then-write in Java.
 *    They are atomic conditional UPDATE statements:
 *      UPDATE user_wallets SET balance = balance - :amt
 *      WHERE user_id = :uid AND balance >= :amt   -- rowcount 0 = insufficient funds
 *      UPDATE user_wallets SET balance = balance + :amt WHERE user_id = :uid
 *  - Workers additionally take a pessimistic (FOR UPDATE) lock on wallet rows,
 *    always in ascending user_id order, to prevent deadlocks when two payments
 *    move money in opposite directions between the same pair of users.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user_wallets")
public class Wallet {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One wallet per user. The FK lives on THIS table (unique) instead of on
     * `users`, so the existing User entity and its save-flows stay untouched.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * BigDecimal, not double: floating point must never touch money.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Wallet(Long userId) {
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
    }
}
