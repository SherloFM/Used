package com.example.Used.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"listing_id", "buyer_id"}))
@Getter @Setter
public class ChatConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    /**
     * Per-user "deleted" flag: hides the conversation from that user's chat
     * list without deleting it for the other participant. A new message in
     * the conversation clears the recipient's flag again.
     */
    private boolean hiddenForBuyer = false;
    private boolean hiddenForSeller = false;

    public boolean isHiddenFor(User user) {
        if (buyer != null && buyer.getId().equals(user.getId())) return hiddenForBuyer;
        if (seller != null && seller.getId().equals(user.getId())) return hiddenForSeller;
        return false;
    }

    public void setHiddenFor(User user, boolean hidden) {
        if (buyer != null && buyer.getId().equals(user.getId())) hiddenForBuyer = hidden;
        else if (seller != null && seller.getId().equals(user.getId())) hiddenForSeller = hidden;
    }
}