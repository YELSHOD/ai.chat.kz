package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
import com.yelshod.ai.chat.kz.auth.CurrentUserResolver;
import com.yelshod.ai.chat.kz.chat.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserResolver currentUserResolver;

    public ChatController(ChatService chatService, CurrentUserResolver currentUserResolver) {
        this.chatService = chatService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ChatResponse createChat(@AuthenticationPrincipal AppPrincipal principal,
                                   @Valid @RequestBody(required = false) CreateChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        CreateChatRequest actualRequest = request == null ? new CreateChatRequest(null) : request;
        return chatService.createChat(userId, actualRequest);
    }

    @GetMapping
    public List<ChatResponse> listChats(@AuthenticationPrincipal AppPrincipal principal) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.listChats(userId);
    }

    @PostMapping("/{chatId}/messages")
    public MessageResponse addMessage(@AuthenticationPrincipal AppPrincipal principal,
                                      @PathVariable UUID chatId,
                                      @Valid @RequestBody CreateMessageRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.addMessage(userId, chatId, request);
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageResponse> listMessages(@AuthenticationPrincipal AppPrincipal principal,
                                              @PathVariable UUID chatId) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.listMessages(userId, chatId);
    }

    @DeleteMapping("/{chatId}")
    public DeleteChatResponse deleteChat(@AuthenticationPrincipal AppPrincipal principal,
                                         @PathVariable UUID chatId) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.deleteChat(userId, chatId);
    }

    @PatchMapping("/{chatId}")
    public ChatResponse renameChat(@AuthenticationPrincipal AppPrincipal principal,
                                   @PathVariable UUID chatId,
                                   @Valid @RequestBody RenameChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.renameChat(userId, chatId, request);
    }

    @GetMapping("/{chatId}/messages/by-day")
    public List<DayMessagesResponse> listMessagesByDay(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable UUID chatId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "Asia/Almaty") String tz
    ) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.listMessagesByDay(userId, chatId, from, to, ZoneId.of(tz));
    }

    @PatchMapping("/{chatId}/project")
    public ChatResponse moveChatToProject(@AuthenticationPrincipal AppPrincipal principal,
                                          @PathVariable UUID chatId,
                                          @Valid @RequestBody MoveChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.moveChatToProject(userId, chatId, request.projectId());
    }

    @DeleteMapping("/{chatId}/project")
    public ChatResponse removeChatFromProject(@AuthenticationPrincipal AppPrincipal principal,
                                              @PathVariable UUID chatId){
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.removeChatFromProject(userId, chatId);
    }

    @PostMapping("/{chatId}/generate")
    public MessageResponse generateAssistantMessage(@AuthenticationPrincipal AppPrincipal principal,
                                                    @PathVariable UUID chatId,
                                                    @Valid @RequestBody(required = false) GenerateChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        GenerateChatRequest actual = request == null ? new GenerateChatRequest(null, null) : request;
        return chatService.generateAssistantMessage(userId, chatId, actual);
    }

    @PostMapping("/{chatId}/regenerate")
    public MessageResponse regenerateAssistantMessage(@AuthenticationPrincipal AppPrincipal principal,
                                                      @PathVariable UUID chatId,
                                                      @Valid @RequestBody(required = false) GenerateChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        GenerateChatRequest actual = request == null ? new GenerateChatRequest(null, null) : request;
        return chatService.regenerateAssistantMessage(userId, chatId, actual);
    }

    @PostMapping(path = "/{chatId}/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateAssistantMessageStream(@AuthenticationPrincipal AppPrincipal principal,
                                                     @PathVariable UUID chatId,
                                                     @Valid @RequestBody(required = false) GenerateChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        GenerateChatRequest actual = request == null ? new GenerateChatRequest(null, null) : request;
        SseEmitter emitter = new SseEmitter(0L);
        chatService.generateAssistantMessageStream(userId, chatId, actual, emitter);
        return emitter;
    }
}
