package com.kuetcord.studycircles.service;

import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.studycircles.access.StudyCircleAccessPolicy;
import com.kuetcord.studycircles.dto.CreateStudySessionRequest;
import com.kuetcord.studycircles.dto.StudySessionDto;
import com.kuetcord.studycircles.event.StudyCircleOutboxEmitter;
import com.kuetcord.studycircles.model.StudyCircle;
import com.kuetcord.studycircles.model.StudySession;
import com.kuetcord.studycircles.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Session scheduler. Times are {@link Instant}s (UTC) end to end; clients send ISO-8601 with an offset
 * and render in the viewer's zone. Scheduling writes {@code STUDY_SESSION_SCHEDULED} to the outbox in the
 * same transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StudySessionService {

    /** Longest allowed session (V12 CHECK); a session that started this long ago has certainly ended. */
    static final Duration MAX_DURATION = Duration.ofMinutes(480);

    private final StudySessionRepository sessionRepository;
    private final CourseOfferingRepository courseRepository;
    private final StudyCircleAccessPolicy accessPolicy;
    private final StudyCircleOutboxEmitter outboxEmitter;

    /** Upcoming sessions (not yet ended, including one in progress); {@code includePast} returns the full history. */
    @Transactional(readOnly = true)
    public List<StudySessionDto> list(Long circleId, Long userId, boolean includePast) {
        accessPolicy.requireMember(circleId, userId);
        if (includePast) {
            return sessionRepository.findSessionsFrom(circleId, Instant.EPOCH);
        }
        return upcoming(circleId, Instant.now());
    }

    public StudySessionDto schedule(Long circleId, Long userId, CreateStudySessionRequest request) {
        StudyCircle circle = accessPolicy.requireMember(circleId, userId);
        Instant start = request.scheduledAt();
        int duration = request.resolvedDurationMinutes();
        Instant end = start.plus(Duration.ofMinutes(duration));

        if (sessionRepository.existsOverlapping(circleId, start, end)) {
            throw new ConflictException("This circle already has a session during that time");
        }

        StudySession session = sessionRepository.saveAndFlush(StudySession.builder()
                .studyCircleId(circleId)
                .title(request.title().strip())
                .scheduledStart(start)
                .durationMinutes(duration)
                .agenda(blankToNull(request.agenda()))
                .meetingLink(blankToNull(request.locationOrLink()))
                .createdBy(userId)
                .build());

        CourseOffering course = circle.getCourseOfferingId() == null ? null
                : courseRepository.findById(circle.getCourseOfferingId()).orElse(null);
        outboxEmitter.sessionScheduled(circle, course, session);

        return sessionRepository.findSessionDto(session.getId()).orElseThrow();
    }

    /** Next session that has not ended yet, or null. Used on the circle page. */
    @Transactional(readOnly = true)
    public StudySessionDto nextSession(Long circleId) {
        List<StudySessionDto> upcoming = upcoming(circleId, Instant.now());
        return upcoming.isEmpty() ? null : upcoming.get(0);
    }

    /** Rows of {@code [circleId, nextStart]} for a directory page. */
    @Transactional(readOnly = true)
    public List<Object[]> nextStarts(Collection<Long> circleIds) {
        return sessionRepository.findNextStarts(circleIds, Instant.now());
    }

    private List<StudySessionDto> upcoming(Long circleId, Instant now) {
        return sessionRepository.findSessionsFrom(circleId, now.minus(MAX_DURATION)).stream()
                .filter(session -> session.endsAt().isAfter(now))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
