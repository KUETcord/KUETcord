package com.kuetcord.studycircles.event;

import com.kuetcord.common.outbox.OutboxEventPublisher;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.studycircles.model.StudyCircle;
import com.kuetcord.studycircles.model.StudySession;
import com.kuetcord.studycircles.repository.StudyCircleMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code STUDY_SESSION_SCHEDULED} to {@code outbox_events} inside the transaction that stores the
 * session ({@link Propagation#MANDATORY}), so a session is never saved without its notification and vice versa.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class StudyCircleOutboxEmitter {

    public static final String SESSION_AGGREGATE = "STUDY_SESSION";
    public static final String STUDY_SESSION_SCHEDULED = "STUDY_SESSION_SCHEDULED";

    private final OutboxEventPublisher outboxEventPublisher;
    private final StudyCircleMemberRepository memberRepository;

    /** Notifies every member except the one who scheduled the session. */
    public void sessionScheduled(StudyCircle circle, CourseOffering course, StudySession session) {
        List<Long> recipients = memberRepository.findUserIds(circle.getId()).stream()
                .filter(userId -> !userId.equals(session.getCreatedBy()))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("studyCircleId", circle.getId());
        payload.put("studyCircleName", circle.getName());
        payload.put("courseOfferingId", course == null ? null : course.getId());
        payload.put("courseCode", course == null ? null : course.getCourseCode());
        payload.put("title", session.getTitle());
        payload.put("scheduledAt", session.getScheduledStart().toString());
        payload.put("durationMinutes", session.getDurationMinutes());
        payload.put("endsAt", session.endsAt().toString());
        payload.put("locationOrLink", session.getMeetingLink());
        payload.put("scheduledById", session.getCreatedBy());
        payload.put("recipientIds", recipients);
        outboxEventPublisher.publish(SESSION_AGGREGATE, String.valueOf(session.getId()), STUDY_SESSION_SCHEDULED, payload);
    }
}
