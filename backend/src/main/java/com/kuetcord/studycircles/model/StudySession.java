package com.kuetcord.studycircles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * A scheduled revision sprint. {@code scheduledStart} is an {@link Instant} stored as
 * {@code TIMESTAMP WITH TIME ZONE}: always UTC on the wire ({@code ...Z}); the UI renders local time.
 */
@Entity
@Table(name = "study_sessions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_circle_id", nullable = false, updatable = false)
    private Long studyCircleId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** Room or meeting link, e.g. "Library 2nd Floor Study Room / Jitsi". */
    @Column(name = "meeting_link", length = 255)
    private String meetingLink;

    @Column(name = "agenda")
    private String agenda;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Instant endsAt() {
        return scheduledStart.plus(Duration.ofMinutes(durationMinutes));
    }
}
