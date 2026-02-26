package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.chat.dto.ChatResponse;
import com.yelshod.ai.chat.kz.chat.dto.CreateChatRequest;
import com.yelshod.ai.chat.kz.chat.dto.CreateMessageRequest;
import com.yelshod.ai.chat.kz.chat.dto.MessageResponse;
import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.user.AppUser;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final AppUserRepository appUserRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(AppUserRepository appUserRepository,
                       ChatRepository chatRepository,
                       ChatMessageRepository chatMessageRepository) {
        this.appUserRepository = appUserRepository;
        this.chatRepository = chatRepository;
        this.chatMessageRepository = chatMessageRepository;
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
