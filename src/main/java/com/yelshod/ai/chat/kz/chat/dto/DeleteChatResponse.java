package com.yelshod.ai.chat.kz.chat.dto;

import java.util.UUID;

public record DeleteChatResponse(
        UUID chatId,
        boolean deleted
) {
}
