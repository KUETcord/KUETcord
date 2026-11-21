package com.kuetcord.playgrounds.service;

import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.playgrounds.access.PlaygroundAccess;
import com.kuetcord.playgrounds.access.PlaygroundAccessPolicy;
import com.kuetcord.playgrounds.dto.AddMemberRequest;
import com.kuetcord.playgrounds.dto.CreatePlaygroundRequest;
import com.kuetcord.playgrounds.dto.PlaygroundDetailDto;
import com.kuetcord.playgrounds.dto.PlaygroundMemberDto;
import com.kuetcord.playgrounds.dto.PlaygroundSummaryDto;
import com.kuetcord.playgrounds.dto.UpdatePlaygroundRequest;
import com.kuetcord.playgrounds.model.Playground;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.PlaygroundRole;
import com.kuetcord.playgrounds.model.TaskStatus;
import com.kuetcord.playgrounds.repository.PlaygroundMemberRepository;
import com.kuetcord.playgrounds.repository.PlaygroundRepository;
import com.kuetcord.playgrounds.repository.ProjectTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Project Playground workspaces: creation (creator becomes LEAD), "my playgrounds", workspace view,
 * metadata edits under optimistic locking, and member invitations.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlaygroundService {

    private final PlaygroundRepository playgroundRepository;
    private final PlaygroundMemberRepository memberRepository;
    private final ProjectTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PlaygroundAccessPolicy accessPolicy;

    public PlaygroundDetailDto create(Long creatorId, CreatePlaygroundRequest request) {
        Playground playground = Playground.builder()
                .title(request.title().strip())
                .problemStatement(blankToNull(request.problemStatement()))
                .visibility(request.resolvedVisibility())
                .repoUrl(blankToNull(request.repoUrl()))
                .demoUrl(blankToNull(request.demoUrl()))
                .ownerId(creatorId)
                .build();
        playground.replaceTags(normalizeTags(request.tags()));
        playground = playgroundRepository.saveAndFlush(playground);

        memberRepository.insertIfAbsent(playground.getId(), creatorId, PlaygroundRole.LEAD.name());
        return toDetail(playground, PlaygroundRole.LEAD);
    }

    @Transactional(readOnly = true)
    public List<PlaygroundSummaryDto> listMine(Long userId) {
        return playgroundRepository.findSummariesForMember(userId);
    }

    @Transactional(readOnly = true)
    public PlaygroundDetailDto getDetail(Long playgroundId, Long userId) {
        PlaygroundAccess access = accessPolicy.require(playgroundId, userId, PlaygroundPermission.VIEW);
        return toDetail(access.playground(), access.role());
    }

    /**
     * LEAD-only metadata edit. Allowed on an ARCHIVED playground so a lead can reopen it by changing
     * {@code status}; every other write path is frozen while archived.
     */
    public PlaygroundDetailDto update(Long playgroundId, Long userId, UpdatePlaygroundRequest request) {
        PlaygroundAccess access = accessPolicy.require(playgroundId, userId, PlaygroundPermission.MANAGE);
        Playground playground = access.playground();
        VersionGuard.assertMatches("Playground", playground.getVersion(), request.expectedVersion());

        if (request.title() != null) {
            playground.setTitle(request.title().strip());
        }
        if (request.problemStatement() != null) {
            playground.setProblemStatement(blankToNull(request.problemStatement()));
        }
        if (request.tags() != null) {
            playground.replaceTags(normalizeTags(request.tags()));
        }
        if (request.visibility() != null) {
            playground.setVisibility(request.visibility());
        }
        if (request.status() != null) {
            playground.setStatus(request.status());
        }
        if (request.repoUrl() != null) {
            playground.setRepoUrl(blankToNull(request.repoUrl()));
        }
        if (request.demoUrl() != null) {
            playground.setDemoUrl(blankToNull(request.demoUrl()));
        }

        return toDetail(playgroundRepository.saveAndFlush(playground), access.role());
    }

    /** LEAD invites an existing student. Inviting someone who is already a member is a 409. */
    public List<PlaygroundMemberDto> addMember(Long playgroundId, Long leadId, AddMemberRequest request) {
        accessPolicy.requireWritable(playgroundId, leadId, PlaygroundPermission.MANAGE);
        if (!userRepository.existsById(request.userId())) {
            throw new ResourceNotFoundException("User not found");
        }
        int inserted = memberRepository.insertIfAbsent(playgroundId, request.userId(), request.resolvedRole().name());
        if (inserted == 0) {
            throw new ConflictException("User is already a member of this playground");
        }
        return memberRepository.findMemberDtos(playgroundId);
    }

    private PlaygroundDetailDto toDetail(Playground playground, PlaygroundRole viewerRole) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : taskRepository.countByStatus(playground.getId())) {
            counts.put((TaskStatus) row[0], (Long) row[1]);
        }

        return new PlaygroundDetailDto(
                playground.getId(),
                playground.getTitle(),
                playground.getProblemStatement(),
                playground.tagList(),
                playground.getVisibility(),
                playground.getStatus(),
                playground.getRepoUrl(),
                playground.getDemoUrl(),
                playground.getOwnerId(),
                viewerRole,
                playground.getVersion(),
                memberRepository.findMemberDtos(playground.getId()),
                Collections.unmodifiableMap(counts),
                playground.getCreatedAt(),
                playground.getUpdatedAt());
    }

    /** Trims tags and drops case-insensitive duplicates while keeping the order the user typed. */
    static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String tag : tags) {
            String trimmed = tag.strip();
            if (!trimmed.isEmpty() && seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
