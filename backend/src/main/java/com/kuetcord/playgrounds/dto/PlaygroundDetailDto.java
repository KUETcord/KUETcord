package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundRole;
import com.kuetcord.playgrounds.model.PlaygroundStatus;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import com.kuetcord.playgrounds.model.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Workspace header: metadata, members, per-column task counts and the contests the team was formed for.
 * {@code myRole} is {@code null} when a non-member views a PUBLIC playground; {@code version} must be
 * echoed back as {@code expectedVersion} on PATCH.
 */
public record PlaygroundDetailDto(
        Long id,
        String title,
        String problemStatement,
        List<String> tags,
        PlaygroundVisibility visibility,
        PlaygroundStatus status,
        String repoUrl,
        String demoUrl,
        Long ownerId,
        PlaygroundRole myRole,
        Long version,
        List<PlaygroundMemberDto> members,
        Map<TaskStatus, Long> taskCounts,
        List<LinkedOpportunityDto> linkedOpportunities,
        Instant createdAt,
        Instant updatedAt
) {
}
