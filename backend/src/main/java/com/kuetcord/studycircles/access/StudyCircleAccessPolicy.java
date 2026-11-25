package com.kuetcord.studycircles.access;

import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.studycircles.model.StudyCircle;
import com.kuetcord.studycircles.model.StudyCircleRole;
import com.kuetcord.studycircles.repository.StudyCircleMemberRepository;
import com.kuetcord.studycircles.repository.StudyCircleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Circles are open: any signed-in student can find one, read its page and join it. Sessions and shared
 * resources are for members, so the directory cannot be scraped for meeting links.
 */
@Component
@RequiredArgsConstructor
public class StudyCircleAccessPolicy {

    private final StudyCircleRepository circleRepository;
    private final StudyCircleMemberRepository memberRepository;

    public StudyCircle requireCircle(Long circleId) {
        return circleRepository.findById(circleId)
                .orElseThrow(() -> new ResourceNotFoundException("Study circle not found"));
    }

    /** @return the circle, after checking the user belongs to it (403 otherwise) */
    public StudyCircle requireMember(Long circleId, Long userId) {
        StudyCircle circle = requireCircle(circleId);
        StudyCircleRole role = memberRepository.findRole(circleId, userId).orElse(null);
        if (role == null) {
            throw new ForbiddenException("Join this study circle to see and share its sessions and resources");
        }
        return circle;
    }
}
