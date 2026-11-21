package com.kuetcord.cthub.dto;

public record CourseFollowStatusDto(
        Long courseOfferingId,
        boolean followed,
        long followerCount
) {
}
