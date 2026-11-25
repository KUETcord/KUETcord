package com.kuetcord.playgrounds.dto;

import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.playgrounds.model.TaskPriority;
import com.kuetcord.playgrounds.model.TaskStatus;

import java.time.Instant;

/** Full task, returned by create / get / update. */
public record TaskDto(
        Long id,
        Long playgroundId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        int position,
        Instant dueDate,
        UserRefDto assignee,
        Long createdBy,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
