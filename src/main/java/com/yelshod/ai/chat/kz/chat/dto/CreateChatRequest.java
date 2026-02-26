package com.yelshod.ai.chat.kz.chat.dto;

import jakarta.validation.constraints.Size;

public record CreateChatRequest(
        @Size(max = 120) String title
) {
}
