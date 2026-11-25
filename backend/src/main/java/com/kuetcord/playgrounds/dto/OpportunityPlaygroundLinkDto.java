package com.kuetcord.playgrounds.dto;

/**
 * Result of linking. {@code playgroundCreated} is true when a new playground was made for the contest;
 * {@code newlyLinked} is false when the link already existed (the call is idempotent).
 */
public record OpportunityPlaygroundLinkDto(
        Long opportunityId,
        String opportunityTitle,
        PlaygroundDetailDto playground,
        boolean playgroundCreated,
        boolean newlyLinked
) {
}
