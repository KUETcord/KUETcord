package com.kuetcord.notifications.outbox;

import com.kuetcord.playgrounds.event.PlaygroundOutboxEmitter;
import com.kuetcord.studycircles.event.StudyCircleOutboxEmitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Member C's playground and study-circle events. Their payloads already carry {@code recipientIds}
 * (computed inside the emitting transaction), so this handler only formats the inbox entry.
 * These are not tied to a chat conversation, so per-conversation Notify/Mute does not apply.
 */
@Component
public class CollaborationEventHandler implements OutboxEventHandler {

    public static final String TARGET_PLAYGROUND = "PLAYGROUND";
    public static final String TARGET_STUDY_CIRCLE = "STUDY_CIRCLE";

    @Override
    public Set<String> eventTypes() {
        return Set.of(
                PlaygroundOutboxEmitter.TASK_ASSIGNED,
                PlaygroundOutboxEmitter.MILESTONE_REACHED,
                StudyCircleOutboxEmitter.STUDY_SESSION_SCHEDULED);
    }

    @Override
    public List<NotificationDraft> toNotifications(ClaimedOutboxEvent event) {
        List<Long> recipients = event.requireLongList("recipientIds");
        return switch (event.eventType()) {
            case PlaygroundOutboxEmitter.TASK_ASSIGNED -> taskAssigned(event, recipients);
            case PlaygroundOutboxEmitter.MILESTONE_REACHED -> drafts(recipients, event.eventType(),
                    "Milestone reached: " + event.text("milestoneTitle"),
                    event.text("playgroundTitle"),
                    TARGET_PLAYGROUND, event.requireLong("playgroundId"));
            case StudyCircleOutboxEmitter.STUDY_SESSION_SCHEDULED -> drafts(recipients, event.eventType(),
                    "New study session: " + event.text("title"),
                    joinNonBlank(event.text("studyCircleName"), event.text("courseCode")),
                    TARGET_STUDY_CIRCLE, event.requireLong("studyCircleId"));
            default -> throw new IllegalArgumentException("Unsupported event type " + event.eventType());
        };
    }

    private List<NotificationDraft> taskAssigned(ClaimedOutboxEvent event, List<Long> recipients) {
        Long assignedBy = event.optionalLong("assignedById");
        // Assigning a card to yourself is not news.
        List<Long> others = recipients.stream().filter(id -> !id.equals(assignedBy)).toList();
        String due = event.text("dueDate");
        return drafts(others, event.eventType(),
                "You were assigned “" + event.text("taskTitle") + "”",
                joinNonBlank(event.text("playgroundTitle"), due == null ? null : "due " + due),
                TARGET_PLAYGROUND, event.requireLong("playgroundId"));
    }

    private static List<NotificationDraft> drafts(List<Long> recipients, String type, String title, String preview,
                                                  String targetType, Long targetId) {
        return recipients.stream()
                .distinct()
                .map(recipientId -> new NotificationDraft(recipientId, type, title, preview, targetType, targetId))
                .toList();
    }

    private static String joinNonBlank(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first + " · " + second;
        }
        return hasFirst ? first : hasSecond ? second : null;
    }
}
