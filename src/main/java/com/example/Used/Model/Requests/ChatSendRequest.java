package com.example.Used.Model.Requests;

import lombok.Data;

@Data
public class ChatSendRequest {
    private Long conversationId;
    private String content;
}