package com.yelshod.ai.chat.kz.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        Long userId,
        String email,
        String username
) {
}
