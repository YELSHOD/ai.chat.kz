package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
import com.yelshod.ai.chat.kz.chat.dto.ChatResponse;
import com.yelshod.ai.chat.kz.chat.dto.CreateChatRequest;
import com.yelshod.ai.chat.kz.chat.dto.CreateMessageRequest;
import com.yelshod.ai.chat.kz.chat.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse createChat(@AuthenticationPrincipal AppPrincipal principal,
                                   @Valid @RequestBody(required = false) CreateChatRequest request) {
        CreateChatRequest actualRequest = request == null ? new CreateChatRequest(null) : request;
        return chatService.createChat(principal.userId(), actualRequest);
    }

    @GetMapping
    public List<ChatResponse> listChats(@AuthenticationPrincipal AppPrincipal principal) {
        return chatService.listChats(principal.userId());
    }

    @PostMapping("/{chatId}/messages")
    public MessageResponse addMessage(@AuthenticationPrincipal AppPrincipal principal,
                                      @PathVariable UUID chatId,
                                      @Valid @RequestBody CreateMessageRequest request) {
        return chatService.addMessage(principal.userId(), chatId, request);
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageResponse> listMessages(@AuthenticationPrincipal AppPrincipal principal,
                                              @PathVariable UUID chatId) {
        return chatService.listMessages(principal.userId(), chatId);
    }
}
