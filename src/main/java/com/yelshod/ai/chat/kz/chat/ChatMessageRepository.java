package com.yelshod.ai.chat.kz.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByChatOrderByCreatedAtAsc(Chat chat);

    List<ChatMessage> findAllByChatAndCreatedAtBetweenOrderByCreatedAtAsc(Chat chat, Instant startInclusive, Instant endExclusive);
}
