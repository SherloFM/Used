package com.example.Used.Controller;

import com.example.Used.Model.Requests.ChatSendRequest;
import com.example.Used.Model.Responses.ChatMessageDto;
import com.example.Used.Service.ChatService;
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