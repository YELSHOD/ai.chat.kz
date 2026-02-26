package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
import com.yelshod.ai.chat.kz.chat.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
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

    @DeleteMapping("/{chatId}")
    public DeleteChatResponse deleteChat(@AuthenticationPrincipal AppPrincipal principal,
                                         @PathVariable UUID chatId) {
        return chatService.deleteChat(principal.userId(), chatId);
    }

    @PatchMapping("/{chatId}")
    public ChatResponse renameChat(@AuthenticationPrincipal AppPrincipal principal,
                                   @PathVariable UUID chatId,
                                   @Valid @RequestBody RenameChatRequest request) {
        return chatService.renameChat(principal.userId(), chatId, request);
    }

    @GetMapping("/{chatId}/messages/by-day")
    public List<DayMessagesResponse> listMessagesByDay(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable UUID chatId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "UTC") String tz
    ) {
        return chatService.listMessagesByDay(principal.userId(), chatId, from, to, ZoneId.of(tz));
    }
}
