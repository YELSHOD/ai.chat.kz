package com.yelshod.ai.chat.kz.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID projectId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
