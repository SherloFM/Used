package com.example.chat.service;

import com.example.chat.client.AccountsClient;
import com.example.chat.client.MarketplaceClient;
import com.example.chat.model.ChatConversation;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.requests.ChatSendRequest;
import com.example.chat.model.responses.ChatConversationDto;
import com.example.chat.model.responses.ChatMessageDto;
import com.example.chat.repository.ChatConversationRepository;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.security.CurrentActorProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatConversationRepository convRepo;
    private final ChatMessageRepository msgRepo;
    private final MarketplaceClient marketplaceClient;
    private final AccountsClient accountsClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentActorProvider currentActorProvider;

    @Transactional
    public ChatConversationDto getOrCreateConversation(Long listingId, Principal principal) {
        Long userId = currentActorProvider.get(principal).id();
        var listing = marketplaceClient.find(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (userId.equals(listing.sellerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot chat with yourself");
        }
        ChatConversation conversation = convRepo.findByListingIdAndBuyerId(listingId, userId)
                .orElseGet(() -> {
                    ChatConversation created = new ChatConversation();
                    created.setListingId(listingId);
                    created.setBuyerId(userId);
                    created.setSellerId(listing.sellerId());
                    return convRepo.save(created);
                });
        conversation.setHiddenFor(userId, false);
        return toConversationDto(conversation, userId, listing.title());
    }

    @Transactional
    public void deleteConversation(Long conversationId, Principal principal) {
        Long userId = currentActorProvider.get(principal).id();
        ChatConversation conversation = loadParticipantConversation(conversationId, userId);
        conversation.setHiddenFor(userId, true);
        convRepo.save(conversation);
    }

    @Transactional
    public ChatMessageDto sendMessage(ChatSendRequest request, Principal principal) {
        Long userId = currentActorProvider.get(principal).id();
        ChatConversation conversation = loadParticipantConversation(request.getConversationId(), userId);
        Long recipientId = otherParticipant(conversation, userId);
        String senderName = displayName(userId);

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSenderId(userId);
        message.setContent(request.getContent());
        message.setSentAt(LocalDateTime.now());
        message.setRead(false);
        msgRepo.save(message);
        conversation.setLastMessageAt(message.getSentAt());
        conversation.setHiddenFor(recipientId, false);
        convRepo.save(conversation);

        ChatMessageDto senderDto = toDto(message, userId, senderName);
        ChatMessageDto recipientDto = toDto(message, recipientId, senderName);
        // Capture DTOs and IDs before commit; do not read managed entities in the callback.
        deliverAfterCommit(() -> {
            deliver(recipientId, recipientDto);
            deliver(userId, senderDto);
        });
        return senderDto;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(Long conversationId, Principal principal) {
        Long userId = currentActorProvider.get(principal).id();
        ChatConversation conversation = loadParticipantConversation(conversationId, userId);
        Map<Long, String> names = new HashMap<>();
        List<ChatMessageDto> result = new ArrayList<>();
        for (ChatMessage message : msgRepo.findByConversationOrderBySentAtAsc(conversation)) {
            result.add(toDto(message, userId, names.computeIfAbsent(message.getSenderId(), this::displayName)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDto> getMyConversations(Principal principal) {
        Long userId = currentActorProvider.get(principal).id();
        List<ChatConversationDto> result = new ArrayList<>();
        for (ChatConversation conversation : convRepo.findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(userId, userId)) {
            if (conversation.isHiddenFor(userId)) {
                continue;
            }
            String title = marketplaceClient.find(conversation.getListingId())
                    .map(listing -> listing.title()).orElse("Unavailable listing");
            result.add(toConversationDto(conversation, userId, title));
        }
        return result;
    }

    private ChatConversation loadParticipantConversation(Long conversationId, Long userId) {
        ChatConversation conversation = convRepo.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!userId.equals(conversation.getBuyerId()) && !userId.equals(conversation.getSellerId())) {
            throw new AccessDeniedException("Not part of conversation");
        }
        return conversation;
    }

    private Long otherParticipant(ChatConversation conversation, Long userId) {
        return userId.equals(conversation.getBuyerId()) ? conversation.getSellerId() : conversation.getBuyerId();
    }

    private String displayName(Long userId) {
        return accountsClient.find(userId).map(user ->
                user.username() != null ? user.username() : user.email()).orElse("Deleted user");
    }

    private ChatConversationDto toConversationDto(ChatConversation conversation, Long userId, String title) {
        ChatConversationDto dto = new ChatConversationDto();
        dto.setConversationId(conversation.getId());
        dto.setListingId(conversation.getListingId());
        dto.setListingTitle(title);
        dto.setOtherUserName(displayName(otherParticipant(conversation, userId)));
        List<ChatMessage> messages = msgRepo.findByConversationOrderBySentAtAsc(conversation);
        if (!messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            dto.setLastMessage(last.getContent());
            dto.setLastMessageAt(last.getSentAt());
        }
        dto.setUnreadCount(msgRepo.countByConversationAndReadFalseAndSenderIdNot(conversation, userId));
        return dto;
    }

    private ChatMessageDto toDto(ChatMessage message, Long viewerId, String senderName) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversation().getId());
        dto.setSenderId(message.getSenderId());
        dto.setSenderName(senderName);
        dto.setContent(message.getContent());
        dto.setSentAt(message.getSentAt());
        dto.setMine(viewerId.equals(message.getSenderId()));
        return dto;
    }

    private void deliverAfterCommit(Runnable delivery) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { delivery.run(); }
            });
        } else {
            delivery.run();
        }
    }

    private void deliver(Long userId, ChatMessageDto dto) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", dto);
        } catch (RuntimeException exception) {
            log.error("Failed to deliver chat message {} to user {} after commit", dto.getId(), userId, exception);
        }
    }
}
