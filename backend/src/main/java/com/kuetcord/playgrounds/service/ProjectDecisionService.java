package com.kuetcord.playgrounds.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.playgrounds.access.PlaygroundAccessPolicy;
import com.kuetcord.playgrounds.dto.CreateDecisionRequest;
import com.kuetcord.playgrounds.dto.DecisionDto;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.ProjectDecision;
import com.kuetcord.playgrounds.repository.ProjectDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Append-only architectural / project decision log, newest entry first. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectDecisionService {

    private final ProjectDecisionRepository decisionRepository;
    private final PlaygroundAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<DecisionDto> list(Long playgroundId, Long userId) {
        accessPolicy.require(playgroundId, userId, PlaygroundPermission.VIEW);
        return decisionRepository.findDecisionLog(playgroundId);
    }

    public DecisionDto record(Long playgroundId, Long userId, CreateDecisionRequest request) {
        accessPolicy.requireWritable(playgroundId, userId, PlaygroundPermission.EDIT_BOARD);
        ProjectDecision saved = decisionRepository.save(ProjectDecision.builder()
                .playgroundId(playgroundId)
                .decision(request.decision().strip())
                .rationale(request.rationale() == null || request.rationale().isBlank()
                        ? null : request.rationale().strip())
                .authorId(userId)
                .build());
        return decisionRepository.findDecisionDto(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Decision not found"));
    }
}
