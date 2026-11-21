package com.kuetcord.playgrounds.service;

import com.kuetcord.playgrounds.access.PlaygroundAccessPolicy;
import com.kuetcord.playgrounds.dto.CreateMilestoneRequest;
import com.kuetcord.playgrounds.dto.MilestoneDto;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.ProjectMilestone;
import com.kuetcord.playgrounds.repository.ProjectMilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Project timeline: dated milestones, listed soonest first. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectMilestoneService {

    private final ProjectMilestoneRepository milestoneRepository;
    private final PlaygroundAccessPolicy accessPolicy;

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

    private static MilestoneDto toDto(ProjectMilestone milestone, Instant now) {
        return new MilestoneDto(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.isOverdueAt(now),
                milestone.getCreatedBy(),
                milestone.getCreatedAt());
    }
}
