package com.example.Used.Model.Responses;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatConversationDto {
    private Long conversationId;
    private Long listingId;
    private String listingTitle;
    private String otherUserName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}