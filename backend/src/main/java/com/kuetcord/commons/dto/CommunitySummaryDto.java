package com.kuetcord.commons.dto;

import com.kuetcord.commons.model.Community;
import com.kuetcord.commons.model.CommunityVisibility;

public record CommunitySummaryDto(
        Long id,
        String name,
        String description,
        CommunityVisibility visibility,
        Long ownerId,
        long memberCount,
        boolean member
) {
    public static CommunitySummaryDto from(Community community, long memberCount, boolean member) {
        return new CommunitySummaryDto(community.getId(), community.getName(), community.getDescription(),
                community.getVisibility(), community.getOwnerId(), memberCount, member);
    }
}
