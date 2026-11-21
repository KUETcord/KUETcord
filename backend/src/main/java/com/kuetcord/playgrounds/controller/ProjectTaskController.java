package com.kuetcord.playgrounds.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.playgrounds.dto.CreateTaskRequest;
import com.kuetcord.playgrounds.dto.KanbanBoardDto;
import com.kuetcord.playgrounds.dto.TaskDto;
import com.kuetcord.playgrounds.dto.UpdateTaskRequest;
import com.kuetcord.playgrounds.service.ProjectTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kanban board of a playground. Updates and deletes require the card version the client last saw;
 * a stale or concurrent change is answered with 409 Conflict so the UI can reload the card.
 */
@RestController
@RequestMapping("/api/v1/playgrounds/{playgroundId}/tasks")
@RequiredArgsConstructor
public class ProjectTaskController {

    private final ProjectTaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<KanbanBoardDto>> board(
            @PathVariable Long playgroundId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.board(playgroundId, principal.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskDto>> create(
            @PathVariable Long playgroundId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Task created", taskService.create(playgroundId, principal.id(), request)));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDto>> get(
            @PathVariable Long playgroundId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.get(playgroundId, taskId, principal.id())));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDto>> update(
            @PathVariable Long playgroundId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.update(playgroundId, taskId, principal.id(), request)));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long playgroundId,
            @PathVariable Long taskId,
            @RequestParam Long expectedVersion,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        taskService.delete(playgroundId, taskId, principal.id(), expectedVersion);
        return ResponseEntity.noContent().build();
    }
}
