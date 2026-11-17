package com.example.chat.model.requests;

import lombok.Data;

@Data
public class ChatSendRequest {
    private Long conversationId;
    private String content;
}