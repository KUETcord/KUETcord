package com.kuetcord.cthub.service;

import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.academic.YearTerm;
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
import com.kuetcord.cthub.dto.RoomAllocationDto;
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
import java.util.Locale;
import java.util.Map;

/**
 * Core CT Hub workflow engine enforcing CR-to-Teacher approval RBAC (1 or 2 combined teachers),
 * multi-room roll-range allocations, State Pattern lifecycle transitions,
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
        return listEvents(courseId, status, null, null, null, from, to);
    }

    @Transactional(readOnly = true)
    public List<CtEventSummaryDto> listEvents(
            Long courseId,
            CtStatus status,
            String department,
            String yearTerm,
            String batch,
            Instant from,
            Instant to) {
        String normalizedYearTerm = (yearTerm != null && !yearTerm.isBlank())
                ? YearTerm.normalizeNullable(yearTerm)
                : null;

        Specification<CtEvent> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (courseId != null) {
                predicates.add(cb.equal(root.get("courseOffering").get("id"), courseId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("targetDepartment")),
                        department.trim().toLowerCase(Locale.ROOT)));
            }
            if (normalizedYearTerm != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("targetYearTerm"), normalizedYearTerm),
                        cb.and(
                                cb.isNull(root.get("targetYearTerm")),
                                cb.equal(root.get("courseOffering").get("yearTerm"), normalizedYearTerm)
                        )
                ));
            }
            if (batch != null && !batch.isBlank()) {
                String normalizedBatch = batch.trim().toLowerCase(Locale.ROOT);
                predicates.add(cb.or(
                        cb.isNull(root.get("targetBatch")),
                        cb.equal(cb.lower(root.get("targetBatch")), normalizedBatch)
                ));
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
        User coTeacher = null;
        if (request.coTeacherId() != null) {
            if (request.coTeacherId().equals(assignedTeacher.getId())) {
                throw new BadRequestException("Combined second teacher must be different from the primary teacher");
            }
            coTeacher = requireActiveTeacher(request.coTeacherId());
        }

        List<RoomAllocationDto> sanitizedAllocations = sanitizeRoomAllocations(request.roomAllocations());
        String summaryRoom = RoomAllocationDto.summarize(sanitizedAllocations);
        String examRoom = summaryRoom != null ? summaryRoom : trimNullable(request.examRoom());
        String targetBatch = trimNullable(request.targetBatch());
        String requestedDept = trimNullable(request.targetDepartment());
        String targetDepartment = requestedDept != null
                ? requestedDept.toUpperCase(Locale.ROOT)
                : (isCrOnly(submitter) && submitter.getDepartment() != null && !submitter.getDepartment().isBlank()
                        ? submitter.getDepartment().trim().toUpperCase(Locale.ROOT)
                        : course.getDepartment().trim().toUpperCase(Locale.ROOT));

        if (isCrOnly(submitter) && submitter.getDepartment() != null && !submitter.getDepartment().isBlank()
                && !targetDepartment.equalsIgnoreCase(submitter.getDepartment().trim())) {
            throw new ForbiddenException("CRs can only propose CTs for their own department (" + submitter.getDepartment() + ")");
        }

        String requestedYearTerm = trimNullable(request.targetYearTerm());
        String targetYearTerm = requestedYearTerm != null
                ? YearTerm.normalizeNullable(requestedYearTerm)
                : course.getYearTerm();

        CtEvent event = CtEvent.builder()
                .courseOffering(course)
                .title(request.title().trim())
                .scheduledAt(request.scheduledAt())
                .durationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 45)
                .examRoom(examRoom)
                .roomAllocations(sanitizedAllocations)
                .targetDepartment(targetDepartment)
                .targetYearTerm(targetYearTerm)
                .targetBatch(targetBatch != null ? targetBatch : course.getTargetBatch())
                .coveredTopics(request.coveredTopics() != null ? request.coveredTopics().trim() : null)
                .status(CtStatus.UNVERIFIED)
                .submittedBy(submitter)
                .assignedTeacher(assignedTeacher)
                .coTeacher(coTeacher)
                .build();

        CtEvent saved = ctEventRepository.saveAndFlush(event);
        String teacherNames = coTeacher != null
                ? assignedTeacher.getDisplayName() + " & " + coTeacher.getDisplayName()
                : assignedTeacher.getDisplayName();

        ctEventHistoryRepository.save(CtEventHistory.builder()
                .ctEvent(saved)
                .previousStatus(null)
                .newStatus(CtStatus.UNVERIFIED)
                .previousScheduledAt(null)
                .newScheduledAt(saved.getScheduledAt())
                .reason("Proposed by " + submitter.getDisplayName() + " for approval by " + teacherNames)
                .changedBy(submitter)
                .build());

        List<User> assignedTeachers = assignedTeachersOf(saved);
        List<Long> teacherIds = assignedTeachers.stream().map(User::getId).toList();

        Map<String, Object> outboxPayload = new LinkedHashMap<>();
        outboxPayload.put("ctEventId", saved.getId());
        outboxPayload.put("courseOfferingId", course.getId());
        outboxPayload.put("courseCode", course.getCourseCode());
        outboxPayload.put("yearTerm", course.getYearTerm());
        outboxPayload.put("title", saved.getTitle());
        outboxPayload.put("scheduledAt", saved.getScheduledAt().toString());
        if (saved.getExamRoom() != null) {
            outboxPayload.put("examRoom", saved.getExamRoom());
        }
        outboxPayload.put("submittedById", submitter.getId());
        outboxPayload.put("submittedByName", submitter.getDisplayName());
        outboxPayload.put("teacherId", assignedTeacher.getId());
        outboxPayload.put("teacherEmail", assignedTeacher.getEmail());
        outboxPayload.put("teacherName", assignedTeacher.getDisplayName());
        outboxPayload.put("teacherIds", teacherIds);
        if (coTeacher != null) {
            outboxPayload.put("coTeacherId", coTeacher.getId());
            outboxPayload.put("coTeacherEmail", coTeacher.getEmail());
            outboxPayload.put("coTeacherName", coTeacher.getDisplayName());
        }

        outboxEventPublisher.publish(AGGREGATE_TYPE, String.valueOf(saved.getId()), "CT_APPROVAL_REQUESTED", outboxPayload);

        String roomSuffix = saved.getExamRoom() != null ? " in room(s) " + saved.getExamRoom() : "";
        for (User teacher : assignedTeachers) {
            if (!teacher.getId().equals(submitter.getId())) {
                domainEventPublisher.publishEvent(new CtTeacherNotificationDomainEvent(
                        saved.getId(),
                        teacher.getEmail(),
                        teacher.getDisplayName(),
                        "[KUETcord] CT Approval Requested: " + course.getCourseCode() + " - " + saved.getTitle(),
                        submitter.getDisplayName() + " proposed \"" + saved.getTitle() + "\" for "
                                + course.getCourseCode() + " scheduled at " + saved.getScheduledAt() + roomSuffix + "."
                ));
            }
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
        if (request.roomAllocations() != null) {
            List<RoomAllocationDto> sanitized = sanitizeRoomAllocations(request.roomAllocations());
            event.setRoomAllocations(sanitized);
            String summary = RoomAllocationDto.summarize(sanitized);
            event.setExamRoom(summary != null ? summary : trimNullable(request.examRoom()));
        } else if (request.examRoom() != null) {
            event.setExamRoom(trimNullable(request.examRoom()));
        }
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
        List<User> assignedTeachers = assignedTeachersOf(saved);
        List<User> teachersToNotify = notifyTeacherIfDifferentActor
                ? assignedTeachers.stream().filter(t -> !t.getId().equals(actor.getId())).toList()
                : List.of();
        boolean notifyTeacher = !teachersToNotify.isEmpty();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ctEventId", saved.getId());
        payload.put("courseOfferingId", saved.getCourseOffering().getId());
        payload.put("courseCode", saved.getCourseOffering().getCourseCode());
        payload.put("yearTerm", saved.getCourseOffering().getYearTerm());
        payload.put("title", saved.getTitle());
        if (saved.getExamRoom() != null) {
            payload.put("examRoom", saved.getExamRoom());
        }
        payload.put("previousStatus", previousStatus.name());
        payload.put("newStatus", saved.getStatus().name());
        payload.put("scheduledAt", saved.getScheduledAt().toString());
        payload.put("reason", reason);
        payload.put("actorId", actor.getId());
        payload.put("actorName", actor.getDisplayName());
        payload.put("followerUserIds", followerIds);
        if (saved.getAssignedTeacher() != null) {
            payload.put("teacherId", saved.getAssignedTeacher().getId());
            payload.put("teacherEmail", saved.getAssignedTeacher().getEmail());
        }
        if (saved.getCoTeacher() != null) {
            payload.put("coTeacherId", saved.getCoTeacher().getId());
            payload.put("coTeacherEmail", saved.getCoTeacher().getEmail());
        }
        payload.put("teacherIds", assignedTeachers.stream().map(User::getId).toList());
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
            for (User teacher : teachersToNotify) {
                domainEventPublisher.publishEvent(new CtTeacherNotificationDomainEvent(
                        saved.getId(),
                        teacher.getEmail(),
                        teacher.getDisplayName(),
                        "[KUETcord] CT " + actionLabel + ": " + saved.getCourseOffering().getCourseCode() + " - " + saved.getTitle(),
                        actor.getDisplayName() + " " + actionLabel + " \"" + saved.getTitle()
                                + "\" (" + saved.getCourseOffering().getCourseCode() + "). Reason: " + reason
                ));
            }
        }
    }

    private List<RoomAllocationDto> sanitizeRoomAllocations(List<RoomAllocationDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<RoomAllocationDto> sanitized = new ArrayList<>();
        for (RoomAllocationDto entry : raw) {
            if (entry == null || entry.room() == null || entry.room().isBlank()) {
                continue;
            }
            Integer start = entry.rollStart();
            Integer end = entry.rollEnd();
            if (start != null && end != null && start > end) {
                throw new BadRequestException(
                        "Invalid roll range for room " + entry.room().trim() + ": rollStart (" + start + ") must be <= rollEnd (" + end + ")");
            }
            sanitized.add(new RoomAllocationDto(start, end, entry.room().trim()));
        }
        return sanitized;
    }

    private List<User> assignedTeachersOf(CtEvent event) {
        List<User> list = new ArrayList<>(2);
        if (event.getAssignedTeacher() != null) {
            list.add(event.getAssignedTeacher());
        }
        if (event.getCoTeacher() != null
                && (event.getAssignedTeacher() == null
                || !event.getCoTeacher().getId().equals(event.getAssignedTeacher().getId()))) {
            list.add(event.getCoTeacher());
        }
        return list;
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
            boolean isPrimary = event.getAssignedTeacher() != null
                    && event.getAssignedTeacher().getId().equals(actor.getId());
            boolean isCombined = event.getCoTeacher() != null
                    && event.getCoTeacher().getId().equals(actor.getId());
            if (isPrimary || isCombined) {
                return;
            }
            throw new ForbiddenException("Only an assigned course teacher can perform this action");
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

    private static boolean isCrOnly(User user) {
        return hasRole(user, RoleName.ROLE_CR)
                && !hasRole(user, RoleName.ROLE_ADMIN)
                && !hasRole(user, RoleName.ROLE_MODERATOR)
                && !hasRole(user, RoleName.ROLE_TEACHER);
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

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
