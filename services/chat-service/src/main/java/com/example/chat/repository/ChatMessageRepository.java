package com.example.chat.repository;

import com.example.chat.model.ChatConversation;
import com.example.chat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationOrderBySentAtAsc(ChatConversation conversation);
    long countByConversationAndReadFalseAndSenderIdNot(ChatConversation conversation, Long senderId);
}
