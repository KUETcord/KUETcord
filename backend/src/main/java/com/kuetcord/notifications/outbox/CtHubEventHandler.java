package com.kuetcord.notifications.outbox;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Member A ↔ Member B Cross-Module Outbox Handler.
 * <p>
 * Translates CT Hub transactional outbox events emitted by {@code CtEventService} into
 * persisted inbox notifications (and post-commit STOMP pushes) via Member B's
 * {@link OutboxProcessor} {@code SKIP LOCKED} worker:
 * <ul>
 *   <li>{@code CT_APPROVAL_REQUESTED} → notifies the assigned KUET course teacher.</li>
 *   <li>{@code CT_VERIFIED} → notifies all course followers (excluding the approving actor).</li>
 *   <li>{@code CT_RESCHEDULED} / {@code CT_CANCELLED} → notifies all course followers and,
 *       when {@code notifyTeacher} is {@code true}, the assigned teacher (deduplicated, excluding actor).</li>
 * </ul>
 */
@Component
public class CtHubEventHandler implements OutboxEventHandler {

    public static final String CT_APPROVAL_REQUESTED = "CT_APPROVAL_REQUESTED";
    public static final String CT_VERIFIED = "CT_VERIFIED";
    public static final String CT_RESCHEDULED = "CT_RESCHEDULED";
    public static final String CT_CANCELLED = "CT_CANCELLED";

    public static final String TARGET_CT_EVENT = "CT_EVENT";

    @Override
    public Set<String> eventTypes() {
        return Set.of(
                CT_APPROVAL_REQUESTED,
                CT_VERIFIED,
                CT_RESCHEDULED,
                CT_CANCELLED
        );
    }

    @Override
    public List<NotificationDraft> toNotifications(ClaimedOutboxEvent event) {
        return switch (event.eventType()) {
            case CT_APPROVAL_REQUESTED -> approvalRequested(event);
            case CT_VERIFIED -> lifecycleUpdate(event, "CT verified: ");
            case CT_RESCHEDULED -> lifecycleUpdate(event, "CT rescheduled: ");
            case CT_CANCELLED -> lifecycleUpdate(event, "CT cancelled: ");
            default -> throw new IllegalArgumentException("Unsupported CT Hub event type: " + event.eventType());
        };
    }

    private List<NotificationDraft> approvalRequested(ClaimedOutboxEvent event) {
        Long ctEventId = event.requireLong("ctEventId");
        Long teacherId = event.optionalLong("teacherId");
        Long submittedById = event.optionalLong("submittedById");
        if (teacherId == null || teacherId.equals(submittedById)) {
            return List.of();
        }

        String title = "CT approval requested: " + safeText(event.text("title"), "Upcoming CT");
        String submittedBy = event.text("submittedByName");
        String preview = joinNonBlank(
                event.text("courseCode"),
                submittedBy == null || submittedBy.isBlank() ? null : "Proposed by " + submittedBy
        );

        return List.of(new NotificationDraft(
                teacherId,
                CT_APPROVAL_REQUESTED,
                title,
                preview,
                TARGET_CT_EVENT,
                ctEventId
        ));
    }

    private List<NotificationDraft> lifecycleUpdate(ClaimedOutboxEvent event, String titlePrefix) {
        Long ctEventId = event.requireLong("ctEventId");
        Long actorId = event.optionalLong("actorId");

        Set<Long> recipients = new LinkedHashSet<>(event.optionalLongList("followerUserIds"));
        boolean notifyTeacher = Boolean.TRUE.equals(event.payload().get("notifyTeacher"));
        Long teacherId = event.optionalLong("teacherId");
        if (notifyTeacher && teacherId != null) {
            recipients.add(teacherId);
        }
        if (actorId != null) {
            recipients.remove(actorId);
        }

        if (recipients.isEmpty()) {
            return List.of();
        }

        String title = titlePrefix + safeText(event.text("title"), "CT Update");
        String preview = joinNonBlank(event.text("courseCode"), event.text("reason"));

        return recipients.stream()
                .map(recipientId -> new NotificationDraft(
                        recipientId,
                        event.eventType(),
                        title,
                        preview,
                        TARGET_CT_EVENT,
                        ctEventId))
                .toList();
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
