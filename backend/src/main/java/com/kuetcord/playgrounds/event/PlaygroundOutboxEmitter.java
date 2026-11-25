package com.kuetcord.playgrounds.event;

import com.kuetcord.common.outbox.OutboxEventPublisher;
import com.kuetcord.playgrounds.model.Playground;
import com.kuetcord.playgrounds.model.ProjectMilestone;
import com.kuetcord.playgrounds.model.ProjectTask;
import com.kuetcord.playgrounds.repository.PlaygroundMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes playground domain events to {@code outbox_events} for the notification engine (Member B).
 * <p>
 * {@link Propagation#MANDATORY}: an event may only be written inside the transaction that changes the
 * task or milestone, so the state change and its notification commit or roll back together. A 409 on a
 * stale card therefore never leaves a stray {@code TASK_ASSIGNED} row behind.
 * <p>
 * Payloads carry {@code recipientIds} so the consumer does not have to re-query playground membership.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PlaygroundOutboxEmitter {

    public static final String TASK_AGGREGATE = "PROJECT_TASK";
    public static final String MILESTONE_AGGREGATE = "PROJECT_MILESTONE";
    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String MILESTONE_REACHED = "MILESTONE_REACHED";

    private final OutboxEventPublisher outboxEventPublisher;
    private final PlaygroundMemberRepository memberRepository;

    public void taskAssigned(Playground playground, ProjectTask task, Long assignedById) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("playgroundId", playground.getId());
        payload.put("playgroundTitle", playground.getTitle());
        payload.put("taskTitle", task.getTitle());
        payload.put("status", task.getStatus().name());
        payload.put("priority", task.getPriority().name());
        payload.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
        payload.put("assigneeId", task.getAssigneeId());
        payload.put("assignedById", assignedById);
        payload.put("recipientIds", List.of(task.getAssigneeId()));
        outboxEventPublisher.publish(TASK_AGGREGATE, String.valueOf(task.getId()), TASK_ASSIGNED, payload);
    }

    /** Notifies every member except the one who marked the milestone complete. */
    public void milestoneReached(Playground playground, ProjectMilestone milestone) {
        List<Long> recipients = memberRepository.findUserIds(playground.getId()).stream()
                .filter(userId -> !userId.equals(milestone.getCompletedBy()))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("milestoneId", milestone.getId());
        payload.put("playgroundId", playground.getId());
        payload.put("playgroundTitle", playground.getTitle());
        payload.put("milestoneTitle", milestone.getTitle());
        payload.put("dueDate", milestone.getDueDate().toString());
        payload.put("completedAt", milestone.getCompletedAt().toString());
        payload.put("completedById", milestone.getCompletedBy());
        payload.put("recipientIds", recipients);
        outboxEventPublisher.publish(MILESTONE_AGGREGATE, String.valueOf(milestone.getId()), MILESTONE_REACHED, payload);
    }
}
