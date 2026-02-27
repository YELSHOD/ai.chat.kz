package com.yelshod.ai.chat.kz.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveChatRequest(
        @NotNull UUID projectId
) {
}
