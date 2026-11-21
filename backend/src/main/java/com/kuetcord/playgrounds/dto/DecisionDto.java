package com.kuetcord.playgrounds.dto;

import java.time.Instant;

public record DecisionDto(
        Long id,
        String decision,
        String rationale,
        UserRefDto author,
        Instant createdAt
) {

    /** Flat constructor used by the JPQL projection (author joined in the same query). */
    public DecisionDto(Long id, String decision, String rationale, Long authorId, String authorName,
                       Instant createdAt) {
        this(id, decision, rationale, UserRefDto.of(authorId, authorName), createdAt);
    }
}
