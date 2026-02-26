package com.yelshod.ai.chat.kz.chat.dto;

import com.yelshod.ai.chat.kz.chat.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID messageId,
        MessageRole role,
        String content,
        Instant createdAt
) {
}
