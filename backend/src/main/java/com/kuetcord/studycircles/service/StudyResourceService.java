package com.kuetcord.studycircles.service;

import com.kuetcord.studycircles.access.StudyCircleAccessPolicy;
import com.kuetcord.studycircles.dto.CreateStudyResourceRequest;
import com.kuetcord.studycircles.dto.StudyResourceDto;
import com.kuetcord.studycircles.model.StudyResource;
import com.kuetcord.studycircles.repository.StudyResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Vetted links shared inside a circle, newest first. Members only. */
@Service
@RequiredArgsConstructor
@Transactional
public class StudyResourceService {

    private final StudyResourceRepository resourceRepository;
    private final StudyCircleAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<StudyResourceDto> list(Long circleId, Long userId) {
        accessPolicy.requireMember(circleId, userId);
        return resourceRepository.findResourceDtos(circleId);
    }

    public StudyResourceDto share(Long circleId, Long userId, CreateStudyResourceRequest request) {
        accessPolicy.requireMember(circleId, userId);
        StudyResource saved = resourceRepository.saveAndFlush(StudyResource.builder()
                .studyCircleId(circleId)
                .title(request.title().strip())
                .resourceUrl(request.url().strip())
                .category(request.category())
                .contributedBy(userId)
                .build());
        return resourceRepository.findResourceDto(saved.getId()).orElseThrow();
    }
}
