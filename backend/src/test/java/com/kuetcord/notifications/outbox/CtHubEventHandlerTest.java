package com.kuetcord.notifications.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtHubEventHandlerTest {

    private final CtHubEventHandler handler = new CtHubEventHandler();

    @Test
    @DisplayName("CT_APPROVAL_REQUESTED produces a notification for the assigned teacher")
    void approvalRequestedNotifiesAssignedTeacher() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                10L,
                CtHubEventHandler.CT_APPROVAL_REQUESTED,
                "42",
                Map.of(
                        "ctEventId", 42L,
                        "courseCode", "CSE 3101",
                        "title", "CT-1: Automata",
                        "submittedById", 1L,
                        "submittedByName", "Tashfiq CR",
                        "teacherId", 5L
                ),
                Instant.now(),
                0
        );

        List<NotificationDraft> drafts = handler.toNotifications(event);
        assertEquals(1, drafts.size());
        NotificationDraft draft = drafts.getFirst();
        assertEquals(5L, draft.recipientId());
        assertEquals(CtHubEventHandler.CT_APPROVAL_REQUESTED, draft.type());
        assertEquals("CT approval requested: CT-1: Automata", draft.title());
        assertEquals("CSE 3101 · Proposed by Tashfiq CR", draft.preview());
        assertEquals(CtHubEventHandler.TARGET_CT_EVENT, draft.targetType());
        assertEquals(42L, draft.targetId());
    }

    @Test
    @DisplayName("CT_VERIFIED notifies course followers and excludes the approving teacher")
    void verifiedNotifiesFollowersExcludingActor() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                11L,
                CtHubEventHandler.CT_VERIFIED,
                "42",
                Map.of(
                        "ctEventId", 42L,
                        "courseCode", "CSE 3101",
                        "title", "CT-1: Automata",
                        "reason", "Approved by course teacher",
                        "actorId", 5L,
                        "followerUserIds", List.of(2L, 3L, 5L),
                        "teacherId", 5L,
                        "notifyTeacher", false
                ),
                Instant.now(),
                0
        );

        List<NotificationDraft> drafts = handler.toNotifications(event);
        assertEquals(2, drafts.size());
        assertEquals(List.of(2L, 3L), drafts.stream().map(NotificationDraft::recipientId).toList());
        assertEquals("CT verified: CT-1: Automata", drafts.getFirst().title());
    }

    @Test
    @DisplayName("CT_RESCHEDULED with notifyTeacher=true notifies followers + assigned teacher, deduplicated and excluding CR actor")
    void rescheduledNotifiesFollowersAndTeacherDeduplicated() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                12L,
                CtHubEventHandler.CT_RESCHEDULED,
                "42",
                Map.of(
                        "ctEventId", 42L,
                        "courseCode", "CSE 3103",
                        "title", "CT-2: 8086",
                        "reason", "Moved after lab clash",
                        "actorId", 1L,
                        "followerUserIds", List.of(1L, 2L, 5L),
                        "teacherId", 5L,
                        "notifyTeacher", true
                ),
                Instant.now(),
                0
        );

        List<NotificationDraft> drafts = handler.toNotifications(event);
        assertEquals(2, drafts.size());
        assertEquals(List.of(2L, 5L), drafts.stream().map(NotificationDraft::recipientId).toList());
        assertEquals("CT rescheduled: CT-2: 8086", drafts.getFirst().title());
        assertEquals("CSE 3103 · Moved after lab clash", drafts.getFirst().preview());
    }

    @Test
    @DisplayName("CT_CANCELLED with no followers still notifies assigned teacher when notifyTeacher=true")
    void cancelledNotifiesTeacherEvenWithNoFollowers() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                13L,
                CtHubEventHandler.CT_CANCELLED,
                "99",
                Map.of(
                        "ctEventId", 99L,
                        "courseCode", "EEE 3110",
                        "title", "CT-3: Signals",
                        "reason", "University holiday",
                        "actorId", 1L,
                        "followerUserIds", List.of(),
                        "teacherId", 5L,
                        "notifyTeacher", true
                ),
                Instant.now(),
                0
        );

        List<NotificationDraft> drafts = handler.toNotifications(event);
        assertEquals(1, drafts.size());
        assertEquals(5L, drafts.getFirst().recipientId());
        assertTrue(drafts.getFirst().title().startsWith("CT cancelled:"));
    }
}
