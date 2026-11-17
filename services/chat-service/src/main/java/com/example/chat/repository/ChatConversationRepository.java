package com.example.chat.repository;

import com.example.chat.model.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findByListingIdAndBuyerId(Long listingId, Long buyerId);
    List<ChatConversation> findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(Long buyerId, Long sellerId);
}
