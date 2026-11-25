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

import java.time.Instant;

/**
 * A peer study group, optionally tied to a course offering from Member A's catalog
 * ({@code study_circles}, V3 + V12).
 */
@Entity
@Table(name = "study_circles")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyCircle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "course_offering_id")
    private Long courseOfferingId;

    /** Shared chat thread owned by the messaging module; linked in Week 6. */
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
