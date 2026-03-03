package com.yelshod.ai.chat.kz.auth.dto;

import java.time.Instant;

public record VerificationChallengeResponse(
        String message,
        Instant expiresAt,
        String verificationToken,
        boolean emailSent
) {
}
