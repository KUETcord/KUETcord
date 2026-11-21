package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.TaskPriority;
import com.kuetcord.playgrounds.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateTaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Long assigneeId,

        Instant dueDate
) {

    public TaskStatus resolvedStatus() {
        return status == null ? TaskStatus.BACKLOG : status;
    }

    public TaskPriority resolvedPriority() {
        return priority == null ? TaskPriority.MEDIUM : priority;
    }
}
