package com.kuetcord.playgrounds.dto;

import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.playgrounds.model.TaskPriority;
import com.kuetcord.playgrounds.model.TaskStatus;

import java.time.Instant;

/**
 * Kanban card as drawn on the board. Filled by a JPQL constructor projection that LEFT JOINs the
 * assignee, so a 150-card board costs one query instead of 1 + N (plan section 4.4).
 * {@code version} is what the client sends back as {@code expectedVersion}.
 */
public record TaskCardDto(
        Long id,
        String title,
        TaskStatus status,
        TaskPriority priority,
        int position,
        Instant dueDate,
        UserRefDto assignee,
        Long version
) {

    /** Flat constructor used by the JPQL projection. */
    public TaskCardDto(Long id, String title, TaskStatus status, TaskPriority priority, Integer position,
                       Instant dueDate, Long assigneeId, String assigneeName, Long version) {
        this(id, title, status, priority, position, dueDate, UserRefDto.of(assigneeId, assigneeName), version);
    }
}
