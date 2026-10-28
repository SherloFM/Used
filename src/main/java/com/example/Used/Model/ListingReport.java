package com.example.Used.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "listing_reports",
        indexes = {
                @Index(name = "idx_reports_listing", columnList = "listing_id"),
                @Index(name = "idx_reports_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ListingReport {

    public enum Reason {
        MISLEADING_LISTING,
        PROHIBITED_ITEM,
        SUSPECTED_SCAM,
        INAPPROPRIATE_CONTENT,
        DUPLICATE_LISTING,
        INCORRECT_INFORMATION,
        OTHER
    }

    public enum Status {
        PENDING,
        UNDER_REVIEW,
        DISMISSED,
        ACTION_TAKEN
    }

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listings listing;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Column(length = 1000)
    private String adminNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
