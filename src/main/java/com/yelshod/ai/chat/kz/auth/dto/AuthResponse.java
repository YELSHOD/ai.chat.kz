package com.yelshod.ai.chat.kz.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String token,
        Instant expiresAt,
        Long userId,
        String email,
        String username
) {
}
