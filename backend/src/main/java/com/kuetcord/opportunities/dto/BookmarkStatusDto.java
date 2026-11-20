package com.kuetcord.opportunities.dto;

public record BookmarkStatusDto(Long opportunityId, boolean bookmarked, long bookmarkCount) {
}
