package com.example.Used.Controller;

import com.example.Used.Model.Requests.ChatSendRequest;
import com.example.Used.Model.Responses.ChatConversationDto;
import com.example.Used.Model.Responses.ChatMessageDto;
import com.example.Used.Service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/conversation")
    public ResponseEntity<ChatConversationDto> getOrCreate(@RequestParam Long listingId, Principal principal) {
        return ResponseEntity.ok(chatService.getOrCreateConversation(listingId, principal));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationDto>> myConversations(Principal principal) {
        return ResponseEntity.ok(chatService.getMyConversations(principal));
    }

    @GetMapping("/conversation/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> messages(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(chatService.getMessages(id, principal));
    }

    @PostMapping("/conversation/{id}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @PathVariable Long id,
            @RequestBody ChatSendRequest request,
            Principal principal
    ) {
        request.setConversationId(id);
        return ResponseEntity.ok(chatService.sendMessage(request, principal));
    }
}