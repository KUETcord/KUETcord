package com.kuetcord.studycircles.repository;

import com.kuetcord.studycircles.dto.StudySessionDto;
import com.kuetcord.studycircles.model.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /**
     * Sessions starting at or after {@code from}, scheduler name joined in, earliest first; served by
     * {@code idx_sessions_circle_start}. Pass {@link Instant#EPOCH} for the full history.
     */
    @Query("""
            SELECT new com.kuetcord.studycircles.dto.StudySessionDto(
                s.id, s.title, s.scheduledStart, s.durationMinutes, s.agenda, s.meetingLink,
                u.id, u.displayName, s.createdAt)
            FROM StudySession s
            LEFT JOIN User u ON u.id = s.createdBy
            WHERE s.studyCircleId = :circleId AND s.scheduledStart >= :from
            ORDER BY s.scheduledStart ASC, s.id ASC
            """)
    List<StudySessionDto> findSessionsFrom(@Param("circleId") Long circleId, @Param("from") Instant from);

    @Query("""
            SELECT new com.kuetcord.studycircles.dto.StudySessionDto(
                s.id, s.title, s.scheduledStart, s.durationMinutes, s.agenda, s.meetingLink,
                u.id, u.displayName, s.createdAt)
            FROM StudySession s
            LEFT JOIN User u ON u.id = s.createdBy
            WHERE s.id = :id
            """)
    Optional<StudySessionDto> findSessionDto(@Param("id") Long id);

    /**
     * True when {@code [start, end)} overlaps another session of the same circle. Two half-open intervals
     * overlap when each starts before the other ends.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM study_sessions
                WHERE study_circle_id = :circleId
                  AND scheduled_start < :endsAt
                  AND scheduled_start + make_interval(mins => duration_minutes) > :startsAt)
            """, nativeQuery = true)
    boolean existsOverlapping(@Param("circleId") Long circleId,
                              @Param("startsAt") Instant startsAt,
                              @Param("endsAt") Instant endsAt);

    /** Rows of {@code [circleId, nextStart]}: the next upcoming start per circle for a directory page. */
    @Query("""
            SELECT s.studyCircleId, MIN(s.scheduledStart)
            FROM StudySession s
            WHERE s.studyCircleId IN :circleIds AND s.scheduledStart > :now
            GROUP BY s.studyCircleId
            """)
    List<Object[]> findNextStarts(@Param("circleIds") Collection<Long> circleIds, @Param("now") Instant now);
}
