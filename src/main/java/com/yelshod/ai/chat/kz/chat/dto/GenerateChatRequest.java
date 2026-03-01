package com.yelshod.ai.chat.kz.chat.dto;

import jakarta.validation.constraints.Size;

public record GenerateChatRequest(
        @Size(max = 15000) String prompt,
        @Size(max = 5000) String systemPrompt
) {
}
