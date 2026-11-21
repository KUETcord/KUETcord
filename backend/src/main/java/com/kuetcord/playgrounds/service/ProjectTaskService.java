package com.kuetcord.playgrounds.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.playgrounds.access.PlaygroundAccessPolicy;
import com.kuetcord.playgrounds.dto.CreateTaskRequest;
import com.kuetcord.playgrounds.dto.KanbanBoardDto;
import com.kuetcord.playgrounds.dto.TaskCardDto;
import com.kuetcord.playgrounds.dto.TaskDto;
import com.kuetcord.playgrounds.dto.UpdateTaskRequest;
import com.kuetcord.playgrounds.dto.UserRefDto;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.PlaygroundRole;
import com.kuetcord.playgrounds.model.ProjectTask;
import com.kuetcord.playgrounds.model.TaskStatus;
import com.kuetcord.playgrounds.repository.PlaygroundMemberRepository;
import com.kuetcord.playgrounds.repository.ProjectTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Kanban board for one playground.
 * <p>
 * Every change to an existing card carries the version the client last saw. A stale version is
 * rejected before anything is written ({@link VersionGuard}); two requests racing on the same version
 * are serialised by PostgreSQL's row lock and the loser's {@code UPDATE ... WHERE version = ?} matches
 * no row, which Spring reports as {@code ObjectOptimisticLockingFailureException}. Both become HTTP 409.
 * <p>
 * Cards may move between any two columns: a Kanban board is a shared picture of the work, not a gated
 * workflow, and teams regularly send a card from REVIEW back to IN_PROGRESS.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectTaskService {

    private final ProjectTaskRepository taskRepository;
    private final PlaygroundMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PlaygroundAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public KanbanBoardDto board(Long playgroundId, Long userId) {
        accessPolicy.require(playgroundId, userId, PlaygroundPermission.VIEW);

        Map<TaskStatus, List<TaskCardDto>> byColumn = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            byColumn.put(status, new ArrayList<>());
        }
        List<TaskCardDto> cards = taskRepository.findBoardCards(playgroundId);
        for (TaskCardDto card : cards) {
            byColumn.get(card.status()).add(card);
        }

        List<KanbanBoardDto.Column> columns = byColumn.entrySet().stream()
                .map(entry -> new KanbanBoardDto.Column(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new KanbanBoardDto(playgroundId, columns, cards.size());
    }

    @Transactional(readOnly = true)
    public TaskDto get(Long playgroundId, Long taskId, Long userId) {
        accessPolicy.require(playgroundId, userId, PlaygroundPermission.VIEW);
        return toDto(requireTask(playgroundId, taskId));
    }

    /** New cards go to the bottom of their column. */
    public TaskDto create(Long playgroundId, Long userId, CreateTaskRequest request) {
        accessPolicy.requireWritable(playgroundId, userId, PlaygroundPermission.EDIT_BOARD);
        if (request.assigneeId() != null) {
            requireAssignable(playgroundId, request.assigneeId());
        }
        TaskStatus status = request.resolvedStatus();

        ProjectTask task = ProjectTask.builder()
                .playgroundId(playgroundId)
                .title(request.title().strip())
                .description(blankToNull(request.description()))
                .status(status)
                .priority(request.resolvedPriority())
                .assigneeId(request.assigneeId())
                .dueDate(request.dueDate())
                .position(nextPosition(playgroundId, status))
                .createdBy(userId)
                .build();
        return toDto(taskRepository.saveAndFlush(task));
    }

    public TaskDto update(Long playgroundId, Long taskId, Long userId, UpdateTaskRequest request) {
        accessPolicy.requireWritable(playgroundId, userId, PlaygroundPermission.EDIT_BOARD);
        ProjectTask task = requireTask(playgroundId, taskId);
        VersionGuard.assertMatches("Task", task.getVersion(), request.expectedVersion());

        // Run every lookup before touching the entity so no query triggers an early auto-flush.
        if (request.assigneeId() != null) {
            requireAssignable(playgroundId, request.assigneeId());
        }
        boolean movesColumn = request.status() != null && request.status() != task.getStatus();
        int newPosition = movesColumn ? nextPosition(playgroundId, request.status()) : task.getPosition();

        if (request.title() != null) {
            task.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            task.setDescription(blankToNull(request.description()));
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.shouldClearAssignee()) {
            task.setAssigneeId(null);
        } else if (request.assigneeId() != null) {
            task.setAssigneeId(request.assigneeId());
        }
        if (request.shouldClearDueDate()) {
            task.setDueDate(null);
        } else if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (movesColumn) {
            task.setStatus(request.status());
            task.setPosition(newPosition);
        }

        return toDto(taskRepository.saveAndFlush(task));
    }

    /** Deleting also needs the current version, so nobody deletes a card a teammate just changed. */
    public void delete(Long playgroundId, Long taskId, Long userId, Long expectedVersion) {
        accessPolicy.requireWritable(playgroundId, userId, PlaygroundPermission.EDIT_BOARD);
        ProjectTask task = requireTask(playgroundId, taskId);
        VersionGuard.assertMatches("Task", task.getVersion(), expectedVersion);
        taskRepository.delete(task);
        taskRepository.flush();
    }

    private ProjectTask requireTask(Long playgroundId, Long taskId) {
        return taskRepository.findByIdAndPlaygroundId(taskId, playgroundId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    /** Only people who can work the board can own a card; VIEWERs and outsiders cannot. */
    private void requireAssignable(Long playgroundId, Long assigneeId) {
        PlaygroundRole role = memberRepository.findRole(playgroundId, assigneeId).orElse(null);
        if (role == null || !role.grants(PlaygroundPermission.EDIT_BOARD)) {
            throw new BadRequestException("Assignee must be a LEAD or CONTRIBUTOR of this playground");
        }
    }

    private int nextPosition(Long playgroundId, TaskStatus status) {
        return taskRepository.findMaxPosition(playgroundId, status) + 1;
    }

    private TaskDto toDto(ProjectTask task) {
        UserRefDto assignee = task.getAssigneeId() == null ? null
                : userRepository.findById(task.getAssigneeId())
                        .map(User::getDisplayName)
                        .map(name -> new UserRefDto(task.getAssigneeId(), name))
                        .orElse(null);
        return new TaskDto(
                task.getId(),
                task.getPlaygroundId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getPosition(),
                task.getDueDate(),
                assignee,
                task.getCreatedBy(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
