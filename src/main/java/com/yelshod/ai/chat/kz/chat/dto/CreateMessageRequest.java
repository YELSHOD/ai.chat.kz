package com.yelshod.ai.chat.kz.chat.dto;

import com.yelshod.ai.chat.kz.chat.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotNull MessageRole role,
        @NotBlank @Size(max = 15000) String content
) {
}
