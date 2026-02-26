package com.yelshod.ai.chat.kz.chat.dto;

import java.time.LocalDate;
import java.util.List;

public record DayMessagesResponse(
        LocalDate date,
        List<MessageResponse> messages
) {
}
