package com.example.Used.Model;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

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
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
