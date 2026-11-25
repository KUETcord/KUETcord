package com.kuetcord.studycircles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** A vetted link shared inside a circle: notes, question bank, slides or a recording. */
@Entity
@Table(name = "study_resources")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_circle_id", nullable = false, updatable = false)
    private Long studyCircleId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "resource_url", nullable = false, length = 500)
    private String resourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ResourceCategory category;

    @Column(name = "contributed_by", nullable = false, updatable = false)
    private Long contributedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
