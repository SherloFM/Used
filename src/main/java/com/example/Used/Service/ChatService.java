package com.example.Used.Service;

import com.example.Used.Model.*;
import com.example.Used.Model.Requests.ChatSendRequest;
import com.example.Used.Model.Responses.ChatConversationDto;
import com.example.Used.Model.Responses.ChatMessageDto;
import com.example.Used.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatConversationRepository convRepo;
    private final ChatMessageRepository msgRepo;
    private final ListingRepository listingRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentUserService currentUserService;

    @Transactional
    public ChatConversationDto getOrCreateConversation(Long listingId, Principal principal) {
        User current = currentUserService.getCurrentUser();
        Listings listing = listingRepo.findById(listingId).orElseThrow();
        if (listing.getUser().getId().equals(current.getId())) {
            throw new RuntimeException("You cannot chat with yourself");
        }
        ChatConversation conversation = convRepo.findByListingAndBuyer(listing, current)
                .orElseGet(() -> {
                    ChatConversation c = new ChatConversation();
                    c.setListing(listing);
                    c.setBuyer(current);
                    c.setSeller(listing.getUser());
                    c.setCreatedAt(LocalDateTime.now());
                    c.setLastMessageAt(LocalDateTime.now());
                    return convRepo.save(c);
                });
        return toConversationDto(conversation, current);
    }

    @Transactional
    public ChatMessageDto sendMessage(ChatSendRequest req, Principal principal) {
        User current = currentUserService.getCurrentUser();
        ChatConversation conv = convRepo.findById(req.getConversationId()).orElseThrow();
        if (!conv.getBuyer().getId().equals(current.getId()) && !conv.getSeller().getId().equals(current.getId())) {
            throw new RuntimeException("Not part of conversation");
        }
        ChatMessage msg = new ChatMessage();
        msg.setConversation(conv);
        msg.setSender(current);
        msg.setContent(req.getContent());
        msg.setSentAt(LocalDateTime.now());
        msg.setRead(false);
        msgRepo.save(msg);
        conv.setLastMessageAt(LocalDateTime.now());
        convRepo.save(conv);

        ChatMessageDto dto = toDto(msg, current);
        // send to recipient
        User recipient = conv.getBuyer().getId().equals(current.getId()) ? conv.getSeller() : conv.getBuyer();
        messagingTemplate.convertAndSendToUser(recipient.getEmail(), "/queue/messages", dto);
        // also echo to sender for consistency
        messagingTemplate.convertAndSendToUser(current.getEmail(), "/queue/messages", dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(Long conversationId, Principal principal) {
        User current = currentUserService.getCurrentUser();
        ChatConversation conv = convRepo.findById(conversationId).orElseThrow();
        if (!conv.getBuyer().getId().equals(current.getId()) && !conv.getSeller().getId().equals(current.getId())) {
            throw new RuntimeException("Not part of conversation");
        }
        List<ChatMessageDto> out = new ArrayList<>();
        for (ChatMessage m : msgRepo.findByConversationOrderBySentAtAsc(conv)) {
            out.add(toDto(m, current));
        }
        // mark read
        // (simple: could mark all from other party as read)
        return out;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDto> getMyConversations(Principal principal) {
        User current = currentUserService.getCurrentUser();
        List<ChatConversation> list = convRepo.findByBuyerOrSellerOrderByLastMessageAtDesc(current, current);
        List<ChatConversationDto> out = new ArrayList<>();
        for (ChatConversation c : list) {
            out.add(toConversationDto(c, current));
        }
        return out;
    }

    private ChatConversationDto toConversationDto(ChatConversation conversation, User current) {
        ChatConversationDto dto = new ChatConversationDto();
        dto.setConversationId(conversation.getId());
        dto.setListingId(conversation.getListing().getId());
        dto.setListingTitle(conversation.getListing().getTitle());
        User other = conversation.getBuyer().getId().equals(current.getId())
                ? conversation.getSeller()
                : conversation.getBuyer();
        dto.setOtherUserName(other.getUsername() != null ? other.getUsername() : other.getEmail());

        List<ChatMessage> messages = msgRepo.findByConversationOrderBySentAtAsc(conversation);
        if (!messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            dto.setLastMessage(last.getContent());
            dto.setLastMessageAt(last.getSentAt());
        }
        dto.setUnreadCount(msgRepo.countByConversationAndReadFalseAndSenderNot(conversation, current));
        return dto;
    }

    private ChatMessageDto toDto(ChatMessage m, User current) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(m.getId());
        dto.setConversationId(m.getConversation().getId());
        dto.setSenderId(m.getSender().getId());
        dto.setSenderName(m.getSender().getUsername() != null ? m.getSender().getUsername() : m.getSender().getEmail());
        dto.setContent(m.getContent());
        dto.setSentAt(m.getSentAt());
        dto.setMine(m.getSender().getId().equals(current.getId()));
        return dto;
    }
}