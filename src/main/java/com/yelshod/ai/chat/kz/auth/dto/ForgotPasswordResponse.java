package com.yelshod.ai.chat.kz.auth.dto;

import java.time.Instant;

public record ForgotPasswordResponse(
        String message,
        Instant expiresAt,
        String resetToken
) {
}
