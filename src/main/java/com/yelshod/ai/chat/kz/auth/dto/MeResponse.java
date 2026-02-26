package com.yelshod.ai.chat.kz.auth.dto;

public record MeResponse(
        Long userId,
        String email,
        String username
) {
}
