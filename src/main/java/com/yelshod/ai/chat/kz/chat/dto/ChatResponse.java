package com.yelshod.ai.chat.kz.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatResponse(
        UUID chatId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
