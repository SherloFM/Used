package com.example.Used.Repository;

import com.example.Used.Model.ChatConversation;
import com.example.Used.Model.Listings;
import com.example.Used.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findByListingAndBuyer(Listings listing, User buyer);
    List<ChatConversation> findByBuyerOrSellerOrderByLastMessageAtDesc(User buyer, User seller);
}