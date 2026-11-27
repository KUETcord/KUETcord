package com.kuetcord.cthub.integration;

import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.cthub.dto.CancelCtEventRequest;
import com.kuetcord.cthub.dto.CourseOfferingDto;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.dto.CreateCtEventRequest;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.dto.RescheduleCtEventRequest;
import com.kuetcord.cthub.dto.VerifyCtEventRequest;
import com.kuetcord.cthub.service.CourseOfferingService;
import com.kuetcord.cthub.service.CtEventService;
import com.kuetcord.notifications.model.Notification;
import com.kuetcord.notifications.outbox.CtHubEventHandler;
import com.kuetcord.notifications.outbox.OutboxProcessor;
import com.kuetcord.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 Cross-Module Integration Test (Member A CT Hub ↔ Member B SKIP LOCKED Outbox & Notifications).
 * <p>
 * Runs without class-level {@code @Transactional} so each CT Hub transaction commits its outbox row
 * and {@link OutboxProcessor#drainAll()} claims and fans it out into {@code notifications} in PostgreSQL 16.
 */
@SpringBootTest
class CtHubToNotificationOutboxIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CourseOfferingService courseOfferingService;

    @Autowired
    private CtEventService ctEventService;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("CT proposal, approval, reschedule, and cancel flow end-to-end through SKIP LOCKED outbox into user notifications")
    void ctHubOutboxEventsDeliverNotificationsToTeacherAndFollowers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User cr = createUser("cr." + suffix + "@stud.kuet.ac.bd", "CR " + suffix, RoleName.ROLE_CR);
        User teacher = createUser("teacher." + suffix + "@cse.kuet.ac.bd", "Dr. " + suffix, RoleName.ROLE_TEACHER);
        User follower = createUser("student." + suffix + "@stud.kuet.ac.bd", "Student " + suffix, RoleName.ROLE_STUDENT);

        CourseOfferingDto course = courseOfferingService.createCourse(new CreateCourseOfferingRequest(
                "CSE-" + suffix.substring(0, 4).toUpperCase(),
                "Software Engineering Cross-Module Integration",
                "CSE",
                "6th",
                "2026"
        ));
        courseOfferingService.followCourse(follower.getId(), course.id());

        // 1. CR proposes CT -> assigned Teacher receives CT_APPROVAL_REQUESTED notification
        CtEventSummaryDto proposed = ctEventService.createEvent(cr.getId(), new CreateCtEventRequest(
                course.id(),
                teacher.getId(),
                "CT-1: Outbox Integration",
                Instant.now().plus(3, ChronoUnit.DAYS),
                45,
                "Transactional Outbox & SKIP LOCKED"
        ));
        outboxProcessor.drainAll();

        List<Notification> teacherInboxAfterPropose = inboxOf(teacher.getId());
        assertTrue(teacherInboxAfterPropose.stream().anyMatch(n ->
                CtHubEventHandler.CT_APPROVAL_REQUESTED.equals(n.getType())
                        && proposed.id().equals(n.getTargetId())));

        // 2. Teacher approves CT -> Follower receives CT_VERIFIED notification
        CtEventSummaryDto verified = ctEventService.verifyEvent(
                proposed.id(),
                teacher.getId(),
                new VerifyCtEventRequest(proposed.version(), "Approved for class")
        );
        outboxProcessor.drainAll();

        List<Notification> followerInboxAfterVerify = inboxOf(follower.getId());
        assertTrue(followerInboxAfterVerify.stream().anyMatch(n ->
                CtHubEventHandler.CT_VERIFIED.equals(n.getType())
                        && verified.id().equals(n.getTargetId())));

        // 3. CR reschedules CT -> BOTH Follower and Teacher receive CT_RESCHEDULED (CR excluded)
        CtEventSummaryDto rescheduled = ctEventService.rescheduleEvent(
                verified.id(),
                cr.getId(),
                new RescheduleCtEventRequest(
                        Instant.now().plus(5, ChronoUnit.DAYS),
                        "Moved after lab viva",
                        verified.version()
                )
        );
        outboxProcessor.drainAll();

        assertTrue(inboxOf(follower.getId()).stream().anyMatch(n ->
                CtHubEventHandler.CT_RESCHEDULED.equals(n.getType()) && rescheduled.id().equals(n.getTargetId())));
        assertTrue(inboxOf(teacher.getId()).stream().anyMatch(n ->
                CtHubEventHandler.CT_RESCHEDULED.equals(n.getType()) && rescheduled.id().equals(n.getTargetId())));
        assertEquals(0, inboxOf(cr.getId()).stream()
                .filter(n -> CtHubEventHandler.CT_RESCHEDULED.equals(n.getType()) && rescheduled.id().equals(n.getTargetId()))
                .count());

        // 4. CR cancels CT -> BOTH Follower and Teacher receive CT_CANCELLED
        ctEventService.cancelEvent(
                rescheduled.id(),
                cr.getId(),
                new CancelCtEventRequest("Merged with final quiz", rescheduled.version())
        );
        outboxProcessor.drainAll();

        assertTrue(inboxOf(follower.getId()).stream().anyMatch(n ->
                CtHubEventHandler.CT_CANCELLED.equals(n.getType()) && rescheduled.id().equals(n.getTargetId())));
        assertTrue(inboxOf(teacher.getId()).stream().anyMatch(n ->
                CtHubEventHandler.CT_CANCELLED.equals(n.getType()) && rescheduled.id().equals(n.getTargetId())));
    }

    private List<Notification> inboxOf(Long userId) {
        return notificationRepository.findByRecipientIdOrderByIdAsc(userId);
    }

    private User createUser(String email, String displayName, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.save(User.builder()
                .email(email)
                .displayName(displayName)
                .username("u_" + UUID.randomUUID().toString().substring(0, 10))
                .passwordHash("$2a$12$K8pQ0q2v6QGZ5oY9N1/cXe5m9mNlZgQ9jUoE/GzO7aH6V3b8o9vC6")
                .department("CSE")
                .batch("2k21")
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(role)))
                .build());
    }
}
