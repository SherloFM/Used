package com.example.chat;

import com.example.chat.model.ChatConversation;
import com.example.chat.model.ChatMessage;
import com.example.chat.repository.ChatConversationRepository;
import com.example.chat.repository.ChatMessageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false"
})
class ChatOwnershipMappingTest {
    @Autowired private ChatConversationRepository conversations;
    @Autowired private ChatMessageRepository messages;
    @Autowired private EntityManager entityManager;

    @Test
    void conversationStoresListingAndParticipantIds() {
        ChatConversation saved = saveConversation(7L, 12L, 18L);
        entityManager.flush();
        entityManager.clear();
        ChatConversation loaded = conversations.findById(saved.getId()).orElseThrow();
        assertEquals(7L, loaded.getListingId());
        assertEquals(12L, loaded.getBuyerId());
        assertEquals(18L, loaded.getSellerId());
    }

    @Test
    void conversationCanBeFoundByListingAndBuyerIds() {
        ChatConversation first = saveConversation(7L, 12L, 18L);
        saveConversation(7L, 13L, 18L);
        entityManager.flush();
        entityManager.clear();
        ChatConversation found = conversations.findByListingIdAndBuyerId(7L, 12L).orElseThrow();
        assertEquals(first.getId(), found.getId());
        assertTrue(conversations.findByListingIdAndBuyerId(7L, 99L).isEmpty());
    }

    @Test
    void participantQueryReturnsOnlyTheirConversationsNewestFirst() {
        ChatConversation older = saveConversation(7L, 12L, 18L);
        older.setLastMessageAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        ChatConversation newer = saveConversation(8L, 20L, 12L);
        newer.setLastMessageAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        saveConversation(9L, 30L, 40L);
        entityManager.flush();
        entityManager.clear();
        var result = conversations.findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(12L, 12L);
        assertEquals(2, result.size());
        assertEquals(newer.getId(), result.get(0).getId());
        assertEquals(older.getId(), result.get(1).getId());
    }

    @Test
    void messageStoresSenderIdAndLocalConversationRelationship() {
        ChatConversation conversation = saveConversation(7L, 12L, 18L);
        ChatMessage saved = saveMessage(conversation, 12L, "Is this available?", false);
        entityManager.flush();
        entityManager.clear();
        ChatMessage loaded = messages.findById(saved.getId()).orElseThrow();
        assertEquals(12L, loaded.getSenderId());
        assertEquals(conversation.getId(), loaded.getConversation().getId());
        assertEquals("Is this available?", loaded.getContent());
    }

    @Test
    void unreadCountExcludesOwnMessagesAndAlreadyReadMessages() {
        ChatConversation conversation = saveConversation(7L, 12L, 18L);
        saveMessage(conversation, 18L, "Hello", false);
        saveMessage(conversation, 18L, "Still interested?", false);
        saveMessage(conversation, 18L, "Earlier message", true);
        saveMessage(conversation, 12L, "My reply", false);
        ChatConversation unrelated = saveConversation(8L, 12L, 20L);
        saveMessage(unrelated, 20L, "Different conversation", false);
        entityManager.flush();
        entityManager.clear();
        ChatConversation loaded = conversations.findById(conversation.getId()).orElseThrow();
        assertEquals(2, messages.countByConversationAndReadFalseAndSenderIdNot(loaded, 12L));
        assertEquals(1, messages.countByConversationAndReadFalseAndSenderIdNot(loaded, 18L));
    }

    @Test
    void hidingConversationAffectsOnlyTheSelectedParticipant() {
        ChatConversation conversation = saveConversation(7L, 12L, 18L);
        conversation.setHiddenFor(12L, true);
        entityManager.flush();
        entityManager.clear();
        ChatConversation loaded = conversations.findById(conversation.getId()).orElseThrow();
        assertTrue(loaded.isHiddenFor(12L));
        assertFalse(loaded.isHiddenFor(18L));
        loaded.setHiddenFor(99L, true);
        assertTrue(loaded.isHiddenFor(12L));
        assertFalse(loaded.isHiddenFor(18L));
        loaded.setHiddenFor(12L, false);
        entityManager.flush();
        entityManager.clear();
        ChatConversation restored = conversations.findById(conversation.getId()).orElseThrow();
        assertFalse(restored.isHiddenFor(12L));
        assertFalse(restored.isHiddenFor(18L));
    }

    private ChatConversation saveConversation(Long listingId, Long buyerId, Long sellerId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setListingId(listingId);
        conversation.setBuyerId(buyerId);
        conversation.setSellerId(sellerId);
        return conversations.save(conversation);
    }

    private ChatMessage saveMessage(ChatConversation conversation, Long senderId, String content, boolean read) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setRead(read);
        return messages.save(message);
    }
}
