package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.TaskStatus;

import java.util.List;

/** The whole board: always five columns in {@link TaskStatus} order, even when a column is empty. */
public record KanbanBoardDto(
        Long playgroundId,
        List<Column> columns,
        int totalTasks
) {

    public record Column(TaskStatus status, List<TaskCardDto> tasks) {
    }
}
