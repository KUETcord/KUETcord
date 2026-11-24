package com.kuetcord.cthub.service;

import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.concurrency.RequestCoalescer;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.common.outbox.OutboxEventPublisher;
import com.kuetcord.cthub.dto.CancelCtEventRequest;
import com.kuetcord.cthub.dto.CreateCtEventRequest;
import com.kuetcord.cthub.dto.CtEventDetailDto;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.dto.RescheduleCtEventRequest;
import com.kuetcord.cthub.dto.VerifyCtEventRequest;
import com.kuetcord.cthub.event.CtStatusChangedDomainEvent;
import com.kuetcord.cthub.event.CtTeacherNotificationDomainEvent;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtEventHistory;
import com.kuetcord.cthub.model.CtStatus;
import com.kuetcord.cthub.repository.CourseFollowRepository;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.cthub.repository.CtEventHistoryRepository;
import com.kuetcord.cthub.repository.CtEventRepository;
import com.kuetcord.cthub.state.CtStateResolver;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core CT Hub workflow engine enforcing CR-to-Teacher approval RBAC, State Pattern lifecycle transitions,
 * JPA {@code @Version} optimistic concurrency control, SingleFlight read coalescing,
 * and atomic transactional outbox + teacher notifications.
 */
@Service
@RequiredArgsConstructor
public class CtEventService {

    private static final String AGGREGATE_TYPE = "CT_EVENT";

    private final CtEventRepository ctEventRepository;
    private final CtEventHistoryRepository ctEventHistoryRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseFollowRepository courseFollowRepository;
    private final UserRepository userRepository;
    private final CtStateResolver ctStateResolver;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ApplicationEventPublisher domainEventPublisher;
    private final RequestCoalescer requestCoalescer;

    @Transactional(readOnly = true)
    public List<CtEventSummaryDto> listEvents(Long courseId, CtStatus status, Instant from, Instant to) {
        Specification<CtEvent> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (courseId != null) {
                predicates.add(cb.equal(root.get("courseOffering").get("id"), courseId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ctEventRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "scheduledAt")).stream()
                .map(CtEventSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CtEventDetailDto getEventDetail(Long ctEventId) {
        return requestCoalescer.coalesce("ct:event:" + ctEventId, () -> {
            CtEvent event = ctEventRepository.findWithDetailsById(ctEventId)
                    .orElseThrow(() -> new ResourceNotFoundException("CT event not found: " + ctEventId));
            List<CtEventHistory> history = ctEventHistoryRepository.findByCtEventIdOrderByChangedAtDesc(ctEventId);
            return CtEventDetailDto.from(event, history);
        });
    }

    @Transactional
    public CtEventSummaryDto createEvent(Long submitterId, CreateCtEventRequest request) {
        CourseOffering course = courseOfferingRepository.findById(request.courseOfferingId())
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + request.courseOfferingId()));
        User submitter = requireUser(submitterId);
        User assignedTeacher = requireActiveTeacher(request.teacherId());

        CtEvent event = CtEvent.builder()
                .courseOffering(course)
                .title(request.title().trim())
                .scheduledAt(request.scheduledAt())
                .durationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 45)
                .coveredTopics(request.coveredTopics() != null ? request.coveredTopics().trim() : null)
                .status(CtStatus.UNVERIFIED)
                .submittedBy(submitter)
                .assignedTeacher(assignedTeacher)
                .build();

        CtEvent saved = ctEventRepository.saveAndFlush(event);
        ctEventHistoryRepository.save(CtEventHistory.builder()
                .ctEvent(saved)
                .previousStatus(null)
                .newStatus(CtStatus.UNVERIFIED)
                .previousScheduledAt(null)
                .newScheduledAt(saved.getScheduledAt())
                .reason("Proposed by " + submitter.getDisplayName() + " for approval by " + assignedTeacher.getDisplayName())
                .changedBy(submitter)
                .build());

        Map<String, Object> outboxPayload = new LinkedHashMap<>();
        outboxPayload.put("ctEventId", saved.getId());
        outboxPayload.put("courseOfferingId", course.getId());
        outboxPayload.put("courseCode", course.getCourseCode());
        outboxPayload.put("title", saved.getTitle());
        outboxPayload.put("scheduledAt", saved.getScheduledAt().toString());
        outboxPayload.put("submittedById", submitter.getId());
        outboxPayload.put("submittedByName", submitter.getDisplayName());
        outboxPayload.put("teacherId", assignedTeacher.getId());
        outboxPayload.put("teacherEmail", assignedTeacher.getEmail());
        outboxPayload.put("teacherName", assignedTeacher.getDisplayName());

        outboxEventPublisher.publish(AGGREGATE_TYPE, String.valueOf(saved.getId()), "CT_APPROVAL_REQUESTED", outboxPayload);

        if (!assignedTeacher.getId().equals(submitter.getId())) {
            domainEventPublisher.publishEvent(new CtTeacherNotificationDomainEvent(
                    saved.getId(),
                    assignedTeacher.getEmail(),
                    assignedTeacher.getDisplayName(),
                    "[KUETcord] CT Approval Requested: " + course.getCourseCode() + " - " + saved.getTitle(),
                    submitter.getDisplayName() + " proposed \"" + saved.getTitle() + "\" for "
                            + course.getCourseCode() + " scheduled at " + saved.getScheduledAt() + "."
            ));
        }

        return CtEventSummaryDto.from(saved);
    }

    @Transactional
    public CtEventSummaryDto verifyEvent(Long ctEventId, Long actorId, VerifyCtEventRequest request) {
        CtEvent event = requireEventWithDetails(ctEventId);
        if (request != null && request.expectedVersion() != null) {
            assertVersionMatch(event, request.expectedVersion());
        }

        User actor = requireUser(actorId);
        assertAssignedTeacherOrAdmin(event, actor, false);

        CtStatus previousStatus = event.getStatus();
        Instant scheduledAt = event.getScheduledAt();

        ctStateResolver.resolve(previousStatus).verify(event, actor);
        CtEvent saved = ctEventRepository.saveAndFlush(event);

        String note = (request != null && request.note() != null && !request.note().isBlank())
                ? request.note().trim()
                : "Approved by course teacher";

        recordAuditAndEmitEvents(saved, previousStatus, scheduledAt, note, actor, "CT_VERIFIED", false);
        return CtEventSummaryDto.from(saved);
    }

    @Transactional
    public CtEventSummaryDto rescheduleEvent(Long ctEventId, Long actorId, RescheduleCtEventRequest request) {
        CtEvent event = requireEventWithDetails(ctEventId);
        assertVersionMatch(event, request.expectedVersion());

        User actor = requireUser(actorId);
        assertAssignedTeacherOrAdmin(event, actor, true);

        CtStatus previousStatus = event.getStatus();
        Instant previousScheduledAt = event.getScheduledAt();

        ctStateResolver.resolve(previousStatus).reschedule(event, request.newScheduledAt(), request.reason().trim(), actor);
        CtEvent saved = ctEventRepository.saveAndFlush(event);

        recordAuditAndEmitEvents(saved, previousStatus, previousScheduledAt, request.reason().trim(), actor, "CT_RESCHEDULED", true);
        return CtEventSummaryDto.from(saved);
    }

    @Transactional
    public CtEventSummaryDto cancelEvent(Long ctEventId, Long actorId, CancelCtEventRequest request) {
        CtEvent event = requireEventWithDetails(ctEventId);
        assertVersionMatch(event, request.expectedVersion());

        User actor = requireUser(actorId);
        assertAssignedTeacherOrAdmin(event, actor, true);

        CtStatus previousStatus = event.getStatus();
        Instant previousScheduledAt = event.getScheduledAt();

        ctStateResolver.resolve(previousStatus).cancel(event, request.reason().trim(), actor);
        CtEvent saved = ctEventRepository.saveAndFlush(event);

        recordAuditAndEmitEvents(saved, previousStatus, previousScheduledAt, request.reason().trim(), actor, "CT_CANCELLED", true);
        return CtEventSummaryDto.from(saved);
    }

    private void recordAuditAndEmitEvents(
            CtEvent saved,
            CtStatus previousStatus,
            Instant previousScheduledAt,
            String reason,
            User actor,
            String outboxEventType,
            boolean notifyTeacherIfDifferentActor) {

        ctEventHistoryRepository.save(CtEventHistory.builder()
                .ctEvent(saved)
                .previousStatus(previousStatus)
                .newStatus(saved.getStatus())
                .previousScheduledAt(previousScheduledAt)
                .newScheduledAt(saved.getScheduledAt())
                .reason(reason)
                .changedBy(actor)
                .build());

        List<Long> followerIds = courseFollowRepository.findFollowerUserIdsByCourseOfferingId(saved.getCourseOffering().getId());
        User assignedTeacher = saved.getAssignedTeacher();
        boolean notifyTeacher = notifyTeacherIfDifferentActor
                && assignedTeacher != null
                && !assignedTeacher.getId().equals(actor.getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ctEventId", saved.getId());
        payload.put("courseOfferingId", saved.getCourseOffering().getId());
        payload.put("courseCode", saved.getCourseOffering().getCourseCode());
        payload.put("title", saved.getTitle());
        payload.put("previousStatus", previousStatus.name());
        payload.put("newStatus", saved.getStatus().name());
        payload.put("scheduledAt", saved.getScheduledAt().toString());
        payload.put("reason", reason);
        payload.put("actorId", actor.getId());
        payload.put("actorName", actor.getDisplayName());
        payload.put("followerUserIds", followerIds);
        if (assignedTeacher != null) {
            payload.put("teacherId", assignedTeacher.getId());
            payload.put("teacherEmail", assignedTeacher.getEmail());
        }
        payload.put("notifyTeacher", notifyTeacher);

        outboxEventPublisher.publish(AGGREGATE_TYPE, String.valueOf(saved.getId()), outboxEventType, payload);

        domainEventPublisher.publishEvent(new CtStatusChangedDomainEvent(
                saved.getId(),
                saved.getCourseOffering().getId(),
                saved.getCourseOffering().getCourseCode(),
                saved.getTitle(),
                previousStatus,
                saved.getStatus(),
                saved.getScheduledAt(),
                actor.getId(),
                reason
        ));

        if (notifyTeacher) {
            String actionLabel = "CT_CANCELLED".equals(outboxEventType) ? "cancelled" : "rescheduled";
            domainEventPublisher.publishEvent(new CtTeacherNotificationDomainEvent(
                    saved.getId(),
                    assignedTeacher.getEmail(),
                    assignedTeacher.getDisplayName(),
                    "[KUETcord] CT " + actionLabel + ": " + saved.getCourseOffering().getCourseCode() + " - " + saved.getTitle(),
                    actor.getDisplayName() + " " + actionLabel + " \"" + saved.getTitle()
                            + "\" (" + saved.getCourseOffering().getCourseCode() + "). Reason: " + reason
            ));
        }
    }

    private void assertAssignedTeacherOrAdmin(CtEvent event, User actor, boolean allowCr) {
        boolean isElevated = hasRole(actor, RoleName.ROLE_ADMIN) || hasRole(actor, RoleName.ROLE_MODERATOR);
        if (isElevated) {
            return;
        }
        if (allowCr && hasRole(actor, RoleName.ROLE_CR)) {
            return;
        }
        if (hasRole(actor, RoleName.ROLE_TEACHER)) {
            if (event.getAssignedTeacher() != null && event.getAssignedTeacher().getId().equals(actor.getId())) {
                return;
            }
            throw new ForbiddenException("Only the assigned course teacher can perform this action");
        }
        throw new ForbiddenException("Insufficient permissions for this CT Hub action");
    }

    private User requireActiveTeacher(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
        if (!teacher.isActive() || !hasRole(teacher, RoleName.ROLE_TEACHER)) {
            throw new BadRequestException("Selected user is not an active KUET course teacher");
        }
        return teacher;
    }

    private static boolean hasRole(User user, RoleName roleName) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == roleName);
    }

    private CtEvent requireEventWithDetails(Long ctEventId) {
        return ctEventRepository.findWithDetailsById(ctEventId)
                .orElseThrow(() -> new ResourceNotFoundException("CT event not found: " + ctEventId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void assertVersionMatch(CtEvent event, Long expectedVersion) {
        if (event.getVersion() == null || !event.getVersion().equals(expectedVersion)) {
            throw new OptimisticLockException(
                    "CT schedule was updated concurrently (expected version " + expectedVersion + ", actual " + event.getVersion() + ")");
        }
    }
}
