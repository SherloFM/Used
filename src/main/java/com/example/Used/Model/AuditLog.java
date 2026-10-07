package com.example.Used.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    public enum AuditAction {
        USER_REGISTERED,
        EMAIL_VERIFIED,
        PASSWORD_RESET,
        PASSWORD_CHANGED,
        USER_BANNED,        // kept for the future ban endpoint
        LISTING_CREATED,
        LISTING_UPDATED,
        LISTING_DELISTED,   // used by BOTH owner-cancel and admin-remove
        LISTING_SOLD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public AuditLog(AuditAction action, User actor, String details) {
        this.action = action;
        this.details = details;
        if (actor != null) {
            this.actorId = actor.getId();
            this.actorUsername = actor.getUsername();
        }
    }



}
