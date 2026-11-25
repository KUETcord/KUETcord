package com.kuetcord.playgrounds.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.playgrounds.access.PlaygroundAccess;
import com.kuetcord.playgrounds.access.PlaygroundAccessPolicy;
import com.kuetcord.playgrounds.dto.CreateMilestoneRequest;
import com.kuetcord.playgrounds.dto.MilestoneDto;
import com.kuetcord.playgrounds.dto.UpdateMilestoneRequest;
import com.kuetcord.playgrounds.event.PlaygroundOutboxEmitter;
import com.kuetcord.playgrounds.model.MilestoneStatus;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.ProjectMilestone;
import com.kuetcord.playgrounds.repository.ProjectMilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Project timeline: dated milestones, listed soonest first. Completing a milestone writes exactly one
 * {@code MILESTONE_REACHED} outbox event, in the same transaction, even when teammates tick it at once.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectMilestoneService {

    private final ProjectMilestoneRepository milestoneRepository;
    private final PlaygroundAccessPolicy accessPolicy;
    private final PlaygroundOutboxEmitter outboxEmitter;

    @Transactional(readOnly = true)
    public List<MilestoneDto> list(Long playgroundId, Long userId) {
        accessPolicy.require(playgroundId, userId, PlaygroundPermission.VIEW);
        Instant now = Instant.now();
        return milestoneRepository.findByPlaygroundIdOrderByDueDateAscIdAsc(playgroundId).stream()
                .map(milestone -> toDto(milestone, now))
                .toList();
    }

    public MilestoneDto create(Long playgroundId, Long userId, CreateMilestoneRequest request) {
        accessPolicy.requireWritable(playgroundId, userId, PlaygroundPermission.EDIT_BOARD);
        ProjectMilestone milestone = milestoneRepository.save(ProjectMilestone.builder()
                .playgroundId(playgroundId)
                .title(request.title().strip())
                .description(request.description() == null || request.description().isBlank()
                        ? null : request.description().strip())
                .dueDate(request.dueDate())
                .createdBy(userId)
                .build());
        return toDto(milestone, Instant.now());
    }

    /**
     * Completes or reopens a milestone. Both directions are idempotent: completing an already completed
     * milestone keeps the original completer and emits nothing.
     */
    public MilestoneDto updateStatus(Long playgroundId, Long milestoneId, Long userId, UpdateMilestoneRequest request) {
        PlaygroundAccess access = accessPolicy.requireWritable(playgroundId, userId, PlaygroundPermission.EDIT_BOARD);
        requireMilestone(playgroundId, milestoneId);

        if (request.status() == MilestoneStatus.COMPLETED) {
            boolean completedNow = milestoneRepository.markCompleted(milestoneId, userId, Instant.now()) == 1;
            ProjectMilestone milestone = requireMilestone(playgroundId, milestoneId);
            if (completedNow) {
                outboxEmitter.milestoneReached(access.playground(), milestone);
            }
            return toDto(milestone, Instant.now());
        }

        milestoneRepository.reopen(milestoneId);
        return toDto(requireMilestone(playgroundId, milestoneId), Instant.now());
    }

    private ProjectMilestone requireMilestone(Long playgroundId, Long milestoneId) {
        return milestoneRepository.findByIdAndPlaygroundId(milestoneId, playgroundId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
    }

    private static MilestoneDto toDto(ProjectMilestone milestone, Instant now) {
        return new MilestoneDto(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.isOverdueAt(now),
                milestone.getCreatedBy(),
                milestone.getCreatedAt(),
                milestone.getCompletedAt(),
                milestone.getCompletedBy());
    }
}
