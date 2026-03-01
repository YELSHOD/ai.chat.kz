package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.ai.GeminiClient;
import com.yelshod.ai.chat.kz.chat.dto.*;
import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.project.Project;
import com.yelshod.ai.chat.kz.project.ProjectRepository;
import com.yelshod.ai.chat.kz.user.AppUser;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final AppUserRepository appUserRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final GeminiClient geminiClient;
    private final int contextMaxMessages;
    private final int contextMaxChars;

    public ChatService(AppUserRepository appUserRepository,
                       ChatRepository chatRepository,
                       ChatMessageRepository chatMessageRepository,
                       ProjectRepository projectRepository,
                       GeminiClient geminiClient,
                       @Value("${ai.context.max-messages:30}") int contextMaxMessages,
                       @Value("${ai.context.max-chars:12000}") int contextMaxChars) {
        this.appUserRepository = appUserRepository;
        this.chatRepository = chatRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.projectRepository = projectRepository;
        this.geminiClient = geminiClient;
        this.contextMaxMessages = contextMaxMessages;
        this.contextMaxChars = contextMaxChars;
    }

    @Transactional
    public ChatResponse createChat(Long userId, CreateChatRequest request) {
        AppUser user = findUser(userId);

        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setUser(user);

        String rawTitle = request.title() == null ? "" : request.title().trim();
        chat.setTitle(rawTitle.isEmpty() ? "New chat" : rawTitle);

        Instant now = Instant.now();
        chat.setCreatedAt(now);
        chat.setUpdatedAt(now);

        Chat saved = chatRepository.save(chat);
        return toChatResponse(saved);
    }

    @Transactional
    public ChatResponse createChatInProject(Long userId, UUID projectPublicId, CreateChatRequest request) {
        AppUser user = findUser(userId);
        Project project = projectRepository.findByPublicIdAndOwner(projectPublicId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setUser(user);
        chat.setProject(project);

        String rawTitle = request.title() == null ? "" : request.title().trim();
        chat.setTitle(rawTitle.isEmpty() ? "New chat" : rawTitle);

        Instant now = Instant.now();
        chat.setCreatedAt(now);
        chat.setUpdatedAt(now);

        Chat saved = chatRepository.save(chat);
        return toChatResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> listChats(Long userId) {
        AppUser user = findUser(userId);
        return chatRepository.findAllByUserOrderByUpdatedAtDesc(user)
                .stream()
                .map(this::toChatResponse)
                .toList();
    }

    @Transactional
    public MessageResponse addMessage(Long userId, UUID chatPublicId, CreateMessageRequest request) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);

        ChatMessage saved = saveChatMessage(chat, request.role(), request.content().trim());
        return toMessageResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(Long userId, UUID chatPublicId) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);

        return chatMessageRepository.findAllByChatOrderByCreatedAtAsc(chat)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public DeleteChatResponse deleteChat(Long userId, UUID chatPublicId) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);
        chatMessageRepository.deleteAll(chatMessageRepository.findAllByChatOrderByCreatedAtAsc(chat));
        chatRepository.delete(chat);
        return new DeleteChatResponse(chatPublicId, true);
    }

    @Transactional
    public ChatResponse renameChat(Long userId, UUID chatPublicId, RenameChatRequest request) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);
        chat.setTitle(request.title().trim());
        chat.setUpdatedAt(Instant.now());
        Chat saved = chatRepository.save(chat);
        return toChatResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DayMessagesResponse> listMessagesByDay(
            Long userId,
            UUID chatPublicId,
            LocalDate from,
            LocalDate to,
            ZoneId zoneId
    ) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);

        Instant start = from.atStartOfDay(zoneId).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<ChatMessage> messages = chatMessageRepository
                .findAllByChatAndCreatedAtBetweenOrderByCreatedAtAsc(chat, start, end);

        Map<LocalDate, List<MessageResponse>> grouped = messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.groupingBy(
                        msg -> msg.createdAt().atZone(zoneId).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.entrySet().stream()
                .map(e -> new DayMessagesResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> listChatsByProject(Long userId, UUID projectPublicId) {
        AppUser user = findUser(userId);
        Project project = projectRepository.findByPublicIdAndOwner(projectPublicId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        return chatRepository.findAllByProjectOrderByUpdatedAtDesc(project)
                .stream()
                .map(this::toChatResponse)
                .toList();
    }

    @Transactional
    public ChatResponse removeChatFromProject(Long userId, UUID chatPublicId) {
        AppUser user = findUser(userId);
        Chat chat = findChat( user, chatPublicId);

        chat.setProject(null);
        chat.setUpdatedAt(Instant.now());
        Chat saved = chatRepository.save(chat);
        return toChatResponse(saved);
    }

    @Transactional
    public ChatResponse moveChatToProject(Long userId, UUID chatPublicId, UUID projectPublicId) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);

        Project project = projectRepository.findByPublicIdAndOwner(projectPublicId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        chat.setProject(project);
        chat.setUpdatedAt(Instant.now());
        Chat saved = chatRepository.save(chat);
        return toChatResponse(saved);
    }

    @Transactional
    public MessageResponse generateAssistantMessage(Long userId, UUID chatPublicId, GenerateChatRequest request) {
        GenerationContext context = prepareGenerationContext(userId, chatPublicId, request, true);
        String generated = geminiClient.generateContent(context.turns(), context.systemInstruction());
        return persistAssistantMessage(userId, chatPublicId, generated);
    }

    @Transactional
    public MessageResponse regenerateAssistantMessage(Long userId, UUID chatPublicId, GenerateChatRequest request) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);
        List<ChatMessage> messages = chatMessageRepository.findAllByChatOrderByCreatedAtAsc(chat);
        if (messages.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chat has no messages");
        }

        ChatMessage last = messages.get(messages.size() - 1);
        if (last.getRole() == MessageRole.ASSISTANT) {
            chatMessageRepository.delete(last);
            chat.setUpdatedAt(Instant.now());
            chatRepository.save(chat);
        }

        GenerationContext context = prepareGenerationContext(userId, chatPublicId, request, false);
        String generated = geminiClient.generateContent(context.turns(), context.systemInstruction());
        return persistAssistantMessage(userId, chatPublicId, generated);
    }

    public void generateAssistantMessageStream(Long userId,
                                               UUID chatPublicId,
                                               GenerateChatRequest request,
                                               SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                GenerationContext context = prepareGenerationContext(userId, chatPublicId, request, true);
                emitter.send(SseEmitter.event().name("start").data(Map.of("chatId", chatPublicId.toString())));

                String generated = geminiClient.streamGenerateContent(context.turns(), context.systemInstruction(), delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (Exception sendException) {
                        throw new RuntimeException(sendException);
                    }
                });

                if (!StringUtils.hasText(generated)) {
                    generated = geminiClient.generateContent(context.turns(), context.systemInstruction());
                }

                MessageResponse saved = persistAssistantMessage(userId, chatPublicId, generated);
                emitter.send(SseEmitter.event().name("done").data(saved));
                emitter.complete();
            } catch (Exception exception) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("Generation failed"));
                } catch (Exception ignored) {
                    // No-op: emitter may already be closed by client.
                }
                emitter.completeWithError(exception);
            }
        });
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Chat findChat(AppUser user, UUID publicId) {
        return chatRepository.findByPublicIdAndUser(publicId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private ChatResponse toChatResponse(Chat chat) {
        return new ChatResponse(chat.getPublicId(), chat.getTitle(), chat.getCreatedAt(), chat.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(message.getPublicId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }

    private GenerationContext prepareGenerationContext(Long userId,
                                                       UUID chatPublicId,
                                                       GenerateChatRequest request,
                                                       boolean appendPrompt) {
        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);

        if (appendPrompt && request != null && StringUtils.hasText(request.prompt())) {
            saveChatMessage(chat, MessageRole.USER, request.prompt().trim());
        }

        List<ChatMessage> allMessages = chatMessageRepository.findAllByChatOrderByCreatedAtAsc(chat);
        if (allMessages.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chat has no messages");
        }

        StringBuilder systemBuilder = new StringBuilder();
        if (request != null && StringUtils.hasText(request.systemPrompt())) {
            systemBuilder.append(request.systemPrompt().trim());
        }

        int maxMessages = Math.max(1, contextMaxMessages);
        int maxChars = Math.max(2000, contextMaxChars);
        List<ChatMessage> selected = selectMessagesWithinLimits(allMessages, maxMessages, maxChars);

        List<GeminiClient.Turn> turns = selected.stream()
                .map(message -> toTurnOrSystem(message, systemBuilder))
                .filter(turn -> turn != null)
                .toList();

        if (turns.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chat has no user/assistant messages for generation");
        }

        return new GenerationContext(turns, systemBuilder.toString().trim());
    }

    private List<ChatMessage> selectMessagesWithinLimits(List<ChatMessage> messages, int maxMessages, int maxChars) {
        List<ChatMessage> selected = new java.util.ArrayList<>();
        int usedChars = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            String content = message.getContent() == null ? "" : message.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }

            int nextChars = usedChars + content.length();
            if (!selected.isEmpty() && (selected.size() >= maxMessages || nextChars > maxChars)) {
                break;
            }

            selected.add(message);
            usedChars = nextChars;
        }

        java.util.Collections.reverse(selected);
        return selected;
    }

    private GeminiClient.Turn toTurnOrSystem(ChatMessage message, StringBuilder systemBuilder) {
        String content = message.getContent() == null ? "" : message.getContent().trim();
        if (content.isEmpty()) {
            return null;
        }

        if (message.getRole() == MessageRole.SYSTEM) {
            if (!systemBuilder.isEmpty()) {
                systemBuilder.append("\n\n");
            }
            systemBuilder.append(content);
            return null;
        }

        String geminiRole = message.getRole() == MessageRole.ASSISTANT ? "model" : "user";
        return new GeminiClient.Turn(geminiRole, content);
    }

    @Transactional
    public MessageResponse persistAssistantMessage(Long userId, UUID chatPublicId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Model returned empty content");
        }

        AppUser user = findUser(userId);
        Chat chat = findChat(user, chatPublicId);
        ChatMessage saved = saveChatMessage(chat, MessageRole.ASSISTANT, content.trim());
        return toMessageResponse(saved);
    }

    private ChatMessage saveChatMessage(Chat chat, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setPublicId(UUID.randomUUID());
        message.setChat(chat);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(Instant.now());

        ChatMessage saved = chatMessageRepository.save(message);
        maybeAutoTitle(chat, role, content);
        chat.setUpdatedAt(Instant.now());
        chatRepository.save(chat);
        return saved;
    }

    private void maybeAutoTitle(Chat chat, MessageRole role, String content) {
        if (role != MessageRole.USER) {
            return;
        }
        if (!"New chat".equals(chat.getTitle())) {
            return;
        }

        String compact = content.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(compact)) {
            return;
        }

        int maxLen = 60;
        if (compact.length() <= maxLen) {
            chat.setTitle(compact);
            return;
        }

        chat.setTitle(compact.substring(0, maxLen - 3).trim() + "...");
    }

    private record GenerationContext(List<GeminiClient.Turn> turns, String systemInstruction) {
    }
}
