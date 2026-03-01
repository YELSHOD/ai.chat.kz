package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
import com.yelshod.ai.chat.kz.chat.dto.*;
import com.yelshod.ai.chat.kz.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse createChat(@AuthenticationPrincipal AppPrincipal principal,
                                   @Valid @RequestBody(required = false) CreateChatRequest request) {
        requirePrincipal(principal);
        CreateChatRequest actualRequest = request == null ? new CreateChatRequest(null) : request;
        return chatService.createChat(principal.userId(), actualRequest);
    }

    @GetMapping
    public List<ChatResponse> listChats(@AuthenticationPrincipal AppPrincipal principal) {
        requirePrincipal(principal);
        return chatService.listChats(principal.userId());
    }

    @PostMapping("/{chatId}/messages")
    public MessageResponse addMessage(@AuthenticationPrincipal AppPrincipal principal,
                                      @PathVariable UUID chatId,
                                      @Valid @RequestBody CreateMessageRequest request) {
        requirePrincipal(principal);
        return chatService.addMessage(principal.userId(), chatId, request);
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageResponse> listMessages(@AuthenticationPrincipal AppPrincipal principal,
                                              @PathVariable UUID chatId) {
        requirePrincipal(principal);
        return chatService.listMessages(principal.userId(), chatId);
    }

    @DeleteMapping("/{chatId}")
    public DeleteChatResponse deleteChat(@AuthenticationPrincipal AppPrincipal principal,
                                         @PathVariable UUID chatId) {
        requirePrincipal(principal);
        return chatService.deleteChat(principal.userId(), chatId);
    }

    @PatchMapping("/{chatId}")
    public ChatResponse renameChat(@AuthenticationPrincipal AppPrincipal principal,
                                   @PathVariable UUID chatId,
                                   @Valid @RequestBody RenameChatRequest request) {
        requirePrincipal(principal);
        return chatService.renameChat(principal.userId(), chatId, request);
    }

    @GetMapping("/{chatId}/messages/by-day")
    public List<DayMessagesResponse> listMessagesByDay(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable UUID chatId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "Asia/Almaty") String tz
    ) {
        requirePrincipal(principal);
        return chatService.listMessagesByDay(principal.userId(), chatId, from, to, ZoneId.of(tz));
    }

    @PatchMapping("/{chatId}/project")
    public ChatResponse moveChatToProject(@AuthenticationPrincipal AppPrincipal principal,
                                          @PathVariable UUID chatId,
                                          @Valid @RequestBody MoveChatRequest request) {
        requirePrincipal(principal);
        return chatService.moveChatToProject(principal.userId(), chatId, request.projectId());
    }

    @DeleteMapping("/{chatId}/project")
    public ChatResponse removeChatFromProject(@AuthenticationPrincipal AppPrincipal principal,
                                              @PathVariable UUID chatId){
        requirePrincipal(principal);
        return chatService.removeChatFromProject(principal.userId(), chatId);
    }

    @PostMapping("/{chatId}/generate")
    public MessageResponse generateAssistantMessage(@AuthenticationPrincipal AppPrincipal principal,
                                                    @PathVariable UUID chatId,
                                                    @Valid @RequestBody(required = false) GenerateChatRequest request) {
        requirePrincipal(principal);
        GenerateChatRequest actual = request == null ? new GenerateChatRequest(null, null) : request;
        return chatService.generateAssistantMessage(principal.userId(), chatId, actual);
    }

    @PostMapping("/{chatId}/regenerate")
    public MessageResponse regenerateAssistantMessage(@AuthenticationPrincipal AppPrincipal principal,
                                                      @PathVariable UUID chatId,
                                                      @Valid @RequestBody(required = false) GenerateChatRequest request) {
        requirePrincipal(principal);
        GenerateChatRequest actual = request == null ? new GenerateChatRequest(null, null) : request;
        return chatService.regenerateAssistantMessage(principal.userId(), chatId, actual);
    }

    @PostMapping(path = "/{chatId}/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateAssistantMessageStream(@AuthenticationPrincipal AppPrincipal principal,
                                                     @PathVariable UUID chatId,
                                                     @Valid @RequestBody(required = false) GenerateChatRequest request) {
        requirePrincipal(principal);
        GenerateChatRequest actual = request == null ? new GenerateChatRequest(null, null) : request;
        SseEmitter emitter = new SseEmitter(0L);
        chatService.generateAssistantMessageStream(principal.userId(), chatId, actual, emitter);
        return emitter;
    }

    private void requirePrincipal(AppPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
    }
}
