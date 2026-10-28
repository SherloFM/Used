package com.example.Used.Repository;

import com.example.Used.Model.ChatConversation;
import com.example.Used.Model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationOrderBySentAtAsc(ChatConversation conversation);
    long countByConversationAndReadFalseAndSenderNot(ChatConversation conversation, com.example.Used.Model.User sender);
}