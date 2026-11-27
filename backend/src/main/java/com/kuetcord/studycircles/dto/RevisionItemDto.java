package com.kuetcord.studycircles.dto;

import com.kuetcord.common.dto.UserRefDto;

import java.time.Instant;

public record RevisionItemDto(
        Long id,
        String title,
        boolean done,
        UserRefDto createdBy,
        UserRefDto completedBy,
        Instant completedAt,
        Instant createdAt
) {
    public RevisionItemDto(Long id, String title, boolean done, Long createdById, String createdByName,
                           Long completedById, String completedByName, Instant completedAt, Instant createdAt) {
        this(id, title, done, UserRefDto.of(createdById, createdByName),
                UserRefDto.of(completedById, completedByName), completedAt, createdAt);
    }
}
