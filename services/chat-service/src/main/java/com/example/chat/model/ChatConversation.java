package com.example.chat.model;


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

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    /**
     * Per-user "deleted" flag: hides the conversation from that user's chat
     * list without deleting it for the other participant. A new message in
     * the conversation clears the recipient's flag again.
     */
    private boolean hiddenForBuyer = false;
    private boolean hiddenForSeller = false;

    public boolean isHiddenFor(Long userId) {
        if (buyerId != null && buyerId.equals(userId)) return hiddenForBuyer;
        if (sellerId != null && sellerId.equals(userId)) return hiddenForSeller;
        return false;
    }

    public void setHiddenFor(Long userId, boolean hidden) {
        if (buyerId != null && buyerId.equals(userId)) hiddenForBuyer = hidden;
        else if (sellerId != null && sellerId.equals(userId)) hiddenForSeller = hidden;
    }
}
