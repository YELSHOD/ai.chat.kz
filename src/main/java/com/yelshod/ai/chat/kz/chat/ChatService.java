package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.chat.dto.*;
import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.project.Project;
import com.yelshod.ai.chat.kz.project.ProjectRepository;
import com.yelshod.ai.chat.kz.user.AppUser;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final AppUserRepository appUserRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;

    public ChatService(AppUserRepository appUserRepository,
                       ChatRepository chatRepository,
                       ChatMessageRepository chatMessageRepository,
                       ProjectRepository projectRepository) {
        this.appUserRepository = appUserRepository;
        this.chatRepository = chatRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.projectRepository = projectRepository;
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

        ChatMessage message = new ChatMessage();
        message.setPublicId(UUID.randomUUID());
        message.setChat(chat);
        message.setRole(request.role());
        message.setContent(request.content().trim());
        message.setCreatedAt(Instant.now());

        ChatMessage saved = chatMessageRepository.save(message);
        chat.setUpdatedAt(Instant.now());
        chatRepository.save(chat);

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
}
