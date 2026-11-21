package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.TaskPriority;
import com.kuetcord.playgrounds.model.TaskStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial task update (drag to another column, re-assign, re-prioritise, edit text).
 * <p>
 * {@code null} fields are left unchanged. Because {@code null} already means "unchanged", removing the
 * assignee or the due date is spelled out with {@code clearAssignee} / {@code clearDueDate}.
 * {@code expectedVersion} is the card version the client last saw; a mismatch is answered with 409.
 */
public record UpdateTaskRequest(
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Long assigneeId,

        Boolean clearAssignee,

        Instant dueDate,

        Boolean clearDueDate,

        @NotNull(message = "expectedVersion is required")
        Long expectedVersion
) {

    @AssertTrue(message = "Title must not be blank")
    public boolean isTitleValid() {
        return title == null || !title.isBlank();
    }

    @AssertTrue(message = "Send either assigneeId or clearAssignee=true, not both")
    public boolean isAssigneeChangeUnambiguous() {
        return !(shouldClearAssignee() && assigneeId != null);
    }

    @AssertTrue(message = "Send either dueDate or clearDueDate=true, not both")
    public boolean isDueDateChangeUnambiguous() {
        return !(shouldClearDueDate() && dueDate != null);
    }

    public boolean shouldClearAssignee() {
        return Boolean.TRUE.equals(clearAssignee);
    }

    public boolean shouldClearDueDate() {
        return Boolean.TRUE.equals(clearDueDate);
    }
}
