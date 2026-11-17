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
 * A single payment attempt for a listing. Source of truth for the money side
 * of a purchase; the {@link Transactions} row is written inside the same DB
 * transaction that completes the payment.
 *
 * State machine:
 *   PENDING -> PROCESSING -> COMPLETED
 *   PENDING/PROCESSING ---> FAILED (with failureReason)
 *   COMPLETED -----------> REFUNDED (future)
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listings listing;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public enum Method {
        WALLET,
        CARD,
        COD
    }

    /**
     * Nullable on purpose: rows created before this column existed are still
     * valid. The application treats null as WALLET.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Set when status = FAILED, e.g. INSUFFICIENT_FUNDS / ALREADY_SOLD / SYSTEM_ERROR */
    @Column
    private String failureReason;

    /** When the worker finished (COMPLETED or FAILED) */
    @Column
    private LocalDateTime processedAt;

    @Column
    private LocalDateTime expiresAt;

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
