package com.kuetcord.studycircles.dto;

import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.studycircles.model.ResourceCategory;

import java.time.Instant;

public record StudyResourceDto(
        Long id,
        String title,
        String url,
        ResourceCategory category,
        UserRefDto contributor,
        Instant createdAt
) {

    /** Flat constructor used by the JPQL projection (contributor joined in the same query). */
    public StudyResourceDto(Long id, String title, String url, ResourceCategory category,
                            Long contributorId, String contributorName, Instant createdAt) {
        this(id, title, url, category, UserRefDto.of(contributorId, contributorName), createdAt);
    }
}
