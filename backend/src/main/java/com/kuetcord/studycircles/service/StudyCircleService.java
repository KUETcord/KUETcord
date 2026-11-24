package com.kuetcord.studycircles.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.studycircles.access.StudyCircleAccessPolicy;
import com.kuetcord.studycircles.dto.CourseRefDto;
import com.kuetcord.studycircles.dto.CreateStudyCircleRequest;
import com.kuetcord.studycircles.dto.MembershipDto;
import com.kuetcord.studycircles.dto.StudyCircleDetailDto;
import com.kuetcord.studycircles.dto.StudyCirclePageDto;
import com.kuetcord.studycircles.dto.StudyCircleSummaryDto;
import com.kuetcord.studycircles.dto.StudySessionDto;
import com.kuetcord.studycircles.model.StudyCircle;
import com.kuetcord.studycircles.model.StudyCircleRole;
import com.kuetcord.studycircles.repository.StudyCircleMemberRepository;
import com.kuetcord.studycircles.repository.StudyCircleRepository;
import com.kuetcord.studycircles.repository.StudyCircleSpecifications;
import com.kuetcord.studycircles.repository.StudyResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Study circle directory, creation (creator becomes HOST), circle page and joining.
 * A directory page costs a fixed number of queries (circles, member counts, the viewer's roles, next
 * sessions, courses) however many cards it shows.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StudyCircleService {

    static final int MAX_PAGE_SIZE = 50;

    private final StudyCircleRepository circleRepository;
    private final StudyCircleMemberRepository memberRepository;
    private final StudyResourceRepository resourceRepository;
    private final CourseOfferingRepository courseRepository;
    private final UserRepository userRepository;
    private final StudyCircleAccessPolicy accessPolicy;
    private final StudySessionService sessionService;

    @Transactional(readOnly = true)
    public StudyCirclePageDto browse(Long viewerId, Long courseOfferingId, String query, boolean mineOnly,
                                     int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<StudyCircle> circles = circleRepository.findAll(
                StudyCircleSpecifications.matching(courseOfferingId, query, mineOnly, viewerId),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        List<Long> ids = circles.getContent().stream().map(StudyCircle::getId).toList();
        if (ids.isEmpty()) {
            return new StudyCirclePageDto(List.of(), safePage, safeSize, circles.getTotalElements());
        }

        Map<Long, Long> memberCounts = toMap(memberRepository.countByCircleIds(ids));
        Map<Long, StudyCircleRole> myRoles = toMap(memberRepository.findRolesForUser(viewerId, ids));
        Map<Long, Instant> nextStarts = toMap(sessionService.nextStarts(ids));
        Map<Long, CourseOffering> courses = courseRepository.findAllById(circles.getContent().stream()
                        .map(StudyCircle::getCourseOfferingId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(CourseOffering::getId, Function.identity()));

        List<StudyCircleSummaryDto> items = circles.getContent().stream()
                .map(circle -> new StudyCircleSummaryDto(
                        circle.getId(),
                        circle.getName(),
                        circle.getDescription(),
                        CourseRefDto.of(courses.get(circle.getCourseOfferingId())),
                        memberCounts.getOrDefault(circle.getId(), 0L),
                        myRoles.get(circle.getId()),
                        nextStarts.get(circle.getId()),
                        circle.getCreatedAt()))
                .toList();
        return new StudyCirclePageDto(items, safePage, safeSize, circles.getTotalElements());
    }

    public StudyCircleDetailDto create(Long creatorId, CreateStudyCircleRequest request) {
        if (request.courseOfferingId() != null && !courseRepository.existsById(request.courseOfferingId())) {
            throw new ResourceNotFoundException("Course offering not found");
        }
        StudyCircle circle = circleRepository.saveAndFlush(StudyCircle.builder()
                .name(request.name().strip())
                .description(request.description() == null || request.description().isBlank()
                        ? null : request.description().strip())
                .courseOfferingId(request.courseOfferingId())
                .creatorId(creatorId)
                .build());
        memberRepository.insertIfAbsent(circle.getId(), creatorId, StudyCircleRole.HOST.name());
        return toDetail(circle, creatorId);
    }

    @Transactional(readOnly = true)
    public StudyCircleDetailDto getDetail(Long circleId, Long viewerId) {
        return toDetail(accessPolicy.requireCircle(circleId), viewerId);
    }

    /** Idempotent: joining twice keeps one row and the original role (a HOST stays HOST). */
    public MembershipDto join(Long circleId, Long userId) {
        accessPolicy.requireCircle(circleId);
        boolean newlyJoined = memberRepository.insertIfAbsent(circleId, userId, StudyCircleRole.ATTENDEE.name()) == 1;
        StudyCircleRole role = memberRepository.findRole(circleId, userId).orElseThrow();
        return new MembershipDto(circleId, role, memberRepository.countByStudyCircleId(circleId), newlyJoined);
    }

    private StudyCircleDetailDto toDetail(StudyCircle circle, Long viewerId) {
        StudyCircleRole myRole = memberRepository.findRole(circle.getId(), viewerId).orElse(null);
        CourseOffering course = circle.getCourseOfferingId() == null ? null
                : courseRepository.findById(circle.getCourseOfferingId()).orElse(null);
        UserRefDto creator = userRepository.findById(circle.getCreatorId())
                .map(User::getDisplayName)
                .map(name -> new UserRefDto(circle.getCreatorId(), name))
                .orElse(null);
        // Members see what is coming up next; outsiders see the circle but not its meeting details.
        StudySessionDto nextSession = myRole == null ? null : sessionService.nextSession(circle.getId());

        return new StudyCircleDetailDto(
                circle.getId(),
                circle.getName(),
                circle.getDescription(),
                CourseRefDto.of(course),
                creator,
                myRole,
                memberRepository.findMemberDtos(circle.getId()),
                nextSession,
                resourceRepository.countByStudyCircleId(circle.getId()),
                circle.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<Long, V> toMap(List<Object[]> rows) {
        Map<Long, V> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (V) row[1]);
        }
        return map;
    }
}
