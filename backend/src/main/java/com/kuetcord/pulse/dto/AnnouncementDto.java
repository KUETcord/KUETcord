package com.kuetcord.pulse.dto;

import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.pulse.model.AnnouncementCategory;

import java.time.Instant;

public record AnnouncementDto(
        Long id,
        String organization,
        AnnouncementCategory category,
        String title,
        String body,
        String sourceUrl,
        Instant publishedAt,
        UserRefDto author
) {
}
