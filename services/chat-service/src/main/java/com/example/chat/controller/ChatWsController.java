package com.example.chat.controller;

import com.example.chat.model.requests.ChatSendRequest;
import com.example.chat.model.responses.ChatMessageDto;
import com.example.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {
    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendRequest req, Principal principal) {
        chatService.sendMessage(req, principal);
    }
}