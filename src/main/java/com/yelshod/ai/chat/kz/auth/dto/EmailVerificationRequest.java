package com.yelshod.ai.chat.kz.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequest(
        @NotBlank String token
) {
}
